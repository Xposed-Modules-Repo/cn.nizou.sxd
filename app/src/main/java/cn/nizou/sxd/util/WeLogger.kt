package cn.nizou.sxd.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * 异步文件日志（移植自 WeKit 的 WeLogger，API 保持一致）。
 *
 * 与 WeKit 的差异：
 * - 无 KnownPaths/BuildConfig.TAG：日志目录取当前进程 Application 的 `filesDir/logs`。
 *   宿主进程（注入）→ 宿主 App 的 filesDir；模块进程（独立设置页）→ 模块自己的 filesDir，
 *   两种上下文都可用，无需跨进程共享存储。
 * - 日志文件前缀 `auto_oral-`（WeKit 为 `wekit-`）：`auto_oral-yyyy-MM-dd.log`。
 * - 内部路径改用 java.io.File（公共 API 仍返回 java.nio.file.Path，与 WeKit 一致）。
 *
 * 行为与 WeKit 相同：
 * - 有界异步队列（ArrayBlockingQueue 2048，重要日志预留 128 容量），daemon writer 线程批量写；
 * - 按天轮转 + 3 天前旧日志自动清理；
 * - e/w/i/d/v（tag+msg / tag+msg+Throwable）、logChunked、currentStackTrace；
 * - [flush] 用 CountDownLatch 屏障等待队列排空并冲刷当前 writer；
 * - 队列满时丢弃并累加计数，writer 追上来后在日志里写一条 dropped 提示。
 */
object WeLogger {

    private const val TAG = "AutoOral"
    private const val LOG_FILE_PREFIX = "auto_oral-"

    private const val CHUNK_SIZE = 4000
    private const val MAX_CHUNKS = 200
    private const val QUEUE_CAPACITY = 2048
    private const val RESERVED_IMPORTANT_CAPACITY = 128
    private const val BATCH_SIZE = 64
    private const val FLUSH_TIMEOUT_MILLIS = 3000L

    private val timestampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private sealed interface WriteTask {
        data class Record(
            val level: String,
            val tag: String?,
            val msg: String,
            val throwable: Throwable?,
            val timestamp: LocalDateTime,
        ) : WriteTask

        class Flush(val completed: CountDownLatch) : WriteTask
    }

    /**
     * A bounded queue keeps logging fire-and-forget even if storage is temporarily slow. A
     * dropped-record counter is emitted by the writer once it catches up, so loss is visible.
     */
    private val writeQueue = ArrayBlockingQueue<WriteTask>(QUEUE_CAPACITY, true)
    private val droppedRecords = AtomicLong()
    private val writerThread = Thread(::runWriter, "AutoOral-Logger").apply {
        isDaemon = true
        start()
    }

    private var writer: FileWriter? = null
    private var currentLogDate: LocalDate? = null

    // ========== Context / Log Dir Resolution ==========

    /** 与 util/crash/CrashLogsManager 相同的注入模式：显式注入优先，否则反射 [currentApplication]。 */
    @Volatile
    private var injectedContext: Context? = null

    @Volatile
    private var cachedContext: Context? = null

    @Volatile
    private var cachedLogsDir: File? = null

    /**
     * 显式注入 Context（例如模块进程早期初始化时）。可选：不调用时自动回退
     * [currentApplication]（宿主注入进程返回宿主 Application，模块进程返回模块 Application）。
     * 注入会同时重置目录缓存，确保日志目录基于注入的 Context 重新解析。
     */
    fun init(context: Context) {
        injectedContext = context
        cachedLogsDir = null
    }

    private fun resolveAppContext(): Context? {
        injectedContext?.let { return it }
        cachedContext?.let { return it }
        val ctx = runCatching { currentApplication() }.getOrNull()
        if (ctx != null) cachedContext = ctx
        return ctx
    }

    /** 当前进程可写的日志目录（`filesDir/logs`），首次访问时创建。 */
    private fun resolveLogsDir(): File? {
        cachedLogsDir?.let { return it }
        val ctx = resolveAppContext() ?: return null
        val dir = runCatching {
            File(ctx.filesDir, "logs").let { d ->
                if (!d.isDirectory && !d.mkdirs()) null else d
            }
        }.getOrNull() ?: return null
        cachedLogsDir = dir
        return dir
    }

    // ========== File Logging Internals ==========

    private fun getOrRotateWriter(logDate: LocalDate): FileWriter? {
        if (writer != null && currentLogDate == logDate) return writer

        writer?.runCatching { close() }
        writer = null
        currentLogDate = null

        val logsDir = resolveLogsDir() ?: return null

        // Clean up logs older than 3 days during rotation/initialization
        deleteOldLogs(logsDir)

        val logFile = File(logsDir, "$LOG_FILE_PREFIX${dateFmt.format(logDate)}.log")

        return runCatching {
            FileWriter(logFile, true).also {
                writer = it
                currentLogDate = logDate
            }
        }.getOrNull()
    }

    private fun deleteOldLogs(logsDir: File) {
        runCatching {
            val thresholdDate = LocalDate.now().minusDays(3)
            val logFileRegex = Regex("""$LOG_FILE_PREFIX(\d{4}-\d{2}-\d{2})\.log""")

            logsDir.listFiles()?.forEach { file ->
                val match = logFileRegex.matchEntire(file.name)
                if (match != null) {
                    val dateStr = match.groupValues[1]
                    val fileDate = runCatching { LocalDate.parse(dateStr, dateFmt) }.getOrNull()

                    // If the log file date is older than 3 days ago, delete it
                    if (fileDate != null && fileDate.isBefore(thresholdDate)) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun runWriter() {
        val batch = ArrayList<WriteTask>(BATCH_SIZE)

        while (!Thread.currentThread().isInterrupted) {
            val first = try {
                writeQueue.take()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            batch += first
            writeQueue.drainTo(batch, BATCH_SIZE - 1)

            var hasWrites = false
            batch.forEach { task ->
                when (task) {
                    is WriteTask.Record -> {
                        writeDroppedNotice(task.timestamp)
                        val written = writeRecord(task)
                        hasWrites = written || hasWrites
                        if (written && (task.level == "E" || task.level == "W" || task.level == "A")) {
                            flushWriter()
                            hasWrites = false
                        }
                    }

                    is WriteTask.Flush -> {
                        try {
                            writeDroppedNotice(LocalDateTime.now())
                            flushWriter()
                        } finally {
                            task.completed.countDown()
                        }
                        hasWrites = false
                    }
                }
            }

            if (hasWrites) flushWriter()
            batch.clear()
        }
    }

    private fun writeRecord(record: WriteTask.Record): Boolean {
        val w = getOrRotateWriter(record.timestamp.toLocalDate()) ?: return false
        return runCatching {
            w.write(buildString {
                append(timestampFmt.format(record.timestamp))
                append(' ')
                append(record.level)
                append('/')
                append(TAG)
                append(' ')
                append(record.tag)
                append(": ")
                append(record.msg)
                if (record.throwable != null) {
                    append('\n')
                    append(Log.getStackTraceString(record.throwable))
                }
            })
            w.write('\n'.code)
            true
        }.getOrElse {
            Log.e(TAG, "failed to write log file", it)
            false
        }
    }

    private fun writeDroppedNotice(timestamp: LocalDateTime) {
        val count = droppedRecords.getAndSet(0)
        if (count == 0L) return

        writeRecord(
            WriteTask.Record(
                level = "W",
                tag = "WeLogger",
                msg = "dropped $count log record(s) because the async queue was full or reserved for important logs",
                throwable = null,
                timestamp = timestamp,
            )
        )
    }

    private fun flushWriter() {
        writer?.runCatching { flush() }
    }

    private fun enqueue(record: WriteTask.Record) {
        val isImportant = record.level == "E" || record.level == "W" || record.level == "A"
        val hasRoom = isImportant || writeQueue.remainingCapacity() > RESERVED_IMPORTANT_CAPACITY
        if (!hasRoom || !writeQueue.offer(record)) {
            droppedRecords.incrementAndGet()
        }
    }

    /**
     * Wait for all records currently queued, then flush the active writer. This is intentionally
     * blocking because it is used at explicit synchronization points such as crash handling and
     * before the log viewer reads the current file; normal log calls never wait for the writer.
     */
    fun flush() {
        if (Thread.currentThread() === writerThread) {
            writeDroppedNotice(LocalDateTime.now())
            flushWriter()
            return
        }

        val completed = CountDownLatch(1)
        val barrier = WriteTask.Flush(completed)
        val enqueued = try {
            writeQueue.offer(barrier, FLUSH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!enqueued) {
            Log.w(TAG, "timed out while enqueueing log flush barrier")
            return
        }

        val finished = try {
            completed.await(FLUSH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!finished) {
            Log.w(TAG, "timed out while flushing log queue")
        }
    }

    // ========== File Logging: public accessors (for the log viewer UI) ==========

    /** The directory run logs are written to (`filesDir/logs`), created on first access. */
    val logsDir: Path?
        get() = resolveLogsDir()?.toPath()

    /**
     * All run-log files (`auto_oral-yyyy-MM-dd.log`), newest first. Flushes the active writer first
     * so the current day's file reflects the latest entries before the UI reads it.
     */
    val allLogFiles: List<Path>
        get() {
            flush()
            val dir = resolveLogsDir() ?: return emptyList()
            val regex = Regex("""$LOG_FILE_PREFIX\d{4}-\d{2}-\d{2}\.log""")
            return runCatching {
                dir.listFiles()
                    ?.filter { it.isFile && regex.matches(it.name) }
                    ?.sortedByDescending { it.name }
                    ?.map { it.toPath() }
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }

    // ========== Tag + String ==========

    fun e(tag: String?, msg: String) {
        Log.e(TAG, "$tag: $msg")
        enqueue(WriteTask.Record("E", tag, msg, null, LocalDateTime.now()))
    }

    fun w(tag: String?, msg: String) {
        Log.w(TAG, "$tag: $msg")
        enqueue(WriteTask.Record("W", tag, msg, null, LocalDateTime.now()))
    }

    fun i(tag: String?, msg: String) {
        Log.i(TAG, "$tag: $msg")
        enqueue(WriteTask.Record("I", tag, msg, null, LocalDateTime.now()))
    }

    fun d(tag: String?, msg: String) {
        Log.d(TAG, "$tag: $msg")
        enqueue(WriteTask.Record("D", tag, msg, null, LocalDateTime.now()))
    }

    fun v(tag: String?, msg: String) {
        Log.v(TAG, "$tag: $msg")
        enqueue(WriteTask.Record("V", tag, msg, null, LocalDateTime.now()))
    }

    // ========== Tag + String + Throwable ==========

    fun e(tag: String?, msg: String, e: Throwable) {
        Log.e(TAG, "$tag: $msg", e)
        enqueue(WriteTask.Record("E", tag, msg, e, LocalDateTime.now()))
    }

    fun w(tag: String?, msg: String, e: Throwable) {
        Log.w(TAG, "$tag: $msg", e)
        enqueue(WriteTask.Record("W", tag, msg, e, LocalDateTime.now()))
    }

    fun i(tag: String?, msg: String, e: Throwable) {
        Log.i(TAG, "$tag: $msg", e)
        enqueue(WriteTask.Record("I", tag, msg, e, LocalDateTime.now()))
    }

    fun d(tag: String?, msg: String, e: Throwable) {
        Log.d(TAG, "$tag: $msg", e)
        enqueue(WriteTask.Record("D", tag, msg, e, LocalDateTime.now()))
    }

    fun v(tag: String?, msg: String, e: Throwable) {
        Log.v(TAG, "$tag: $msg", e)
        enqueue(WriteTask.Record("V", tag, msg, e, LocalDateTime.now()))
    }

    // ========== Stack Trace ==========

    val currentStackTrace: String
        get() {
            return Thread.currentThread().stackTrace
                .drop(2) // drop getStackTrace + this function
                .joinToString(separator = "\n") { element ->
                    "at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})"
                }
        }

    // ========== Chunked ==========

    fun logChunked(priority: Int, tag: String, msg: String) {
        if (msg.length <= CHUNK_SIZE) {
            Log.println(priority, TAG, "$tag: $msg")
            enqueue(WriteTask.Record(priority.toPriorityChar(), tag, msg, null, LocalDateTime.now()))
            return
        }

        val len = msg.length
        val chunkCount = (len + CHUNK_SIZE - 1) / CHUNK_SIZE
        if (chunkCount > MAX_CHUNKS) {
            val head = msg.substring(0, CHUNK_SIZE)
            val headMsg = "[chunked] too long ($len chars, $chunkCount chunks). head:\n$head"
            val truncMsg = "[chunked] truncated. consider writing to file for full dump."
            Log.println(priority, TAG, "$tag: $headMsg")
            Log.println(priority, TAG, "$tag: $truncMsg")
            val timestamp = LocalDateTime.now()
            enqueue(WriteTask.Record(priority.toPriorityChar(), tag, headMsg, null, timestamp))
            enqueue(WriteTask.Record(priority.toPriorityChar(), tag, truncMsg, null, timestamp))
            return
        }

        var i = 0
        var part = 1
        val timestamp = LocalDateTime.now()
        while (i < len) {
            val end = min(i + CHUNK_SIZE, len)
            val chunk = msg.substring(i, end)
            val partMsg = "[part $part/$chunkCount] $chunk"
            Log.println(priority, TAG, "$tag: $partMsg")
            enqueue(WriteTask.Record(priority.toPriorityChar(), tag, partMsg, null, timestamp))
            i += CHUNK_SIZE
            part++
        }
    }

    fun logChunkedI(tag: String, msg: String) = logChunked(Log.INFO, tag, msg)
    fun logChunkedD(tag: String, msg: String) = logChunked(Log.DEBUG, tag, msg)

    // ========== Helpers ==========

    private fun Int.toPriorityChar(): String = when (this) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }
}
