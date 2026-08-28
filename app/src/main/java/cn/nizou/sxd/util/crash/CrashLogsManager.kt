package cn.nizou.sxd.util.crash

import android.content.Context
import cn.nizou.sxd.util.WeLogger
import cn.nizou.sxd.util.currentApplication
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 崩溃日志管理器
 * 负责保存/读取/删除崩溃日志，维护 pending 崩溃标记，日志上限 50 个
 *
 * 移植自 wekit utils/crash/CrashLogsManager.kt；native 崩溃标记保留（与 wekit 对齐），
 * 但 NativeCrashHandler 不移植。目录取 `filesDir/crashes`（与 WeLogger 同根 filesDir，
 * run-log 走 filesDir/logs）。内部日志走 t1 的 [WeLogger]。
 */
object CrashLogsManager {

    private const val TAG = "CrashLogsManager"

    private const val CRASH_LOGS_DIR = "crashes"

    // 对齐项目 run-log 命名（logs/auto_oral-*.log）："auto_oral-crash-" 前缀 + 毫秒时间戳
    private const val CRASH_LOGS_PREFIX = "auto_oral-crash-"
    private const val CRASH_LOG_SUFFIX = ".log"
    private const val PENDING_CRASH_FLAG = "pending_crash.flag"
    private const val PENDING_JAVA_CRASH_FLAG = "pending_java_crash.flag"
    private const val MAX_LOG_FILES = 50
    private const val MAX_LOG_CONTENT_SIZE = 30 * 1024

    /** 显式注入的应用 Context（与 WeLogger.init 相同模式）；为空时回退反射取宿主 Application */
    @Volatile
    private var injectedContext: Context? = null

    /** 供 t4 接线时显式初始化（可选）；不调用也能通过 [currentApplication] 兜底 */
    fun init(context: Context) {
        injectedContext = context.applicationContext
    }

    private fun appContext(): Context? =
        injectedContext ?: runCatching { currentApplication() }.getOrNull()

    private fun crashLogsDir(): Path? {
        val ctx = appContext() ?: return null
        return runCatching { ctx.filesDir.toPath().resolve(CRASH_LOGS_DIR) }.getOrNull()
    }

    private fun ensureCrashLogDirExists(): Path? {
        val dir = crashLogsDir() ?: return null
        if (!dir.exists()) {
            if (runCatching { dir.createDirectories() }.isSuccess) {
                WeLogger.i(TAG, "Crash log directory created: ${dir.absolutePathString()}")
            } else {
                WeLogger.e(TAG, "Failed to create crash log directory")
            }
        }
        return dir
    }

    fun saveCrashLog(crashInfo: String, isJavaCrash: Boolean = false): String? {
        return try {
            val dir = ensureCrashLogDirExists() ?: return null

            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.getDefault())
            val fileName = CRASH_LOGS_PREFIX + sdf.format(Date()) + CRASH_LOG_SUFFIX
            val logFile = dir / fileName

            logFile.writeText(crashInfo)
            WeLogger.i(TAG, "crash log saved: ${logFile.absolutePathString()}")

            if (isJavaCrash) setPendingJavaCrashFlag(logFile.name)
            else setPendingCrashFlag(logFile.name)

            cleanOldLogs()
            logFile.absolutePathString()
        } catch (e: IOException) {
            WeLogger.e(TAG, "failed to save crash log", e)
            null
        }
    }

    val allCrashLogs: List<Path>
        get() {
            val crashLogsDir = ensureCrashLogDirExists() ?: return emptyList()
            return crashLogsDir.listDirectoryEntries()
                .filter {
                    it.name.startsWith(CRASH_LOGS_PREFIX) && it.name.endsWith(CRASH_LOG_SUFFIX)
                }
                .sortedByDescending { it.getLastModifiedTime() }
        }

    fun readCrashLog(logFile: Path): String? {
        return try {
            if (!logFile.exists() || !logFile.isRegularFile()) return null

            val fileSize = logFile.fileSize()
            if (fileSize > MAX_LOG_CONTENT_SIZE) {
                WeLogger.w(
                    TAG,
                    "crash log file is too large ($fileSize bytes), reading first $MAX_LOG_CONTENT_SIZE bytes"
                )
                val buffer = ByteArray(MAX_LOG_CONTENT_SIZE)
                val bytesRead = logFile.inputStream().use { it.read(buffer) }
                String(buffer, 0, bytesRead, StandardCharsets.UTF_8) +
                        "\n\n========================================\n" +
                        "【提示】日志内容过长，此处仅展示部分内容。\n" +
                        "请点击「导出文件」以保存完整日志。\n" +
                        "========================================"
            } else {
                logFile.readText()
            }
        } catch (e: IOException) {
            WeLogger.e(TAG, "failed to read crash log", e)
            null
        }
    }

    fun readFullCrashLog(logFile: Path): String? {
        return try {
            if (!logFile.exists() || !logFile.isRegularFile()) return null
            WeLogger.d(TAG, "Reading full crash log, size: ${logFile.fileSize()} bytes")
            logFile.readText()
        } catch (e: IOException) {
            WeLogger.e(TAG, "failed to read full crash log", e)
            null
        }
    }

    fun deleteCrashLog(logFile: Path): Boolean {
        return if (logFile.exists() && runCatching { logFile.deleteExisting() }.isSuccess) {
            WeLogger.i(TAG, "Crash log deleted: ${logFile.name}")
            true
        } else false
    }

    fun deleteAllCrashLogs(): Int {
        val count = allCrashLogs.count { deleteCrashLog(it) }
        clearPendingCrashFlag()
        clearPendingJavaCrashFlag()
        WeLogger.i(TAG, "deleted $count crash logs")
        return count
    }

    private fun cleanOldLogs() {
        val logFiles = allCrashLogs
        if (logFiles.size > MAX_LOG_FILES) {
            WeLogger.i(TAG, "Cleaning old crash logs, current count: ${logFiles.size}")
            logFiles.drop(MAX_LOG_FILES).forEach { deleteCrashLog(it) }
        }
    }

    // ========== pending crash flag（通用，预留 native 崩溃） ==========

    private fun setPendingCrashFlag(logFileName: String) {
        try {
            val dir = crashLogsDir() ?: return
            (dir / PENDING_CRASH_FLAG).writeText(logFileName)
            WeLogger.d(TAG, "pending crash flag set: $logFileName")
        } catch (e: IOException) {
            WeLogger.e(TAG, "failed to set pending crash flag", e)
        }
    }

    val pendingCrashLogFileName: String?
        get() = readFlagFile(PENDING_CRASH_FLAG, "pending crash")

    val pendingCrashLogFile: Path?
        get() {
            val fileName = pendingCrashLogFileName ?: return null
            val dir = crashLogsDir() ?: return null
            val logFile = dir / fileName
            if (logFile.exists() && logFile.isRegularFile()) return logFile
            clearPendingCrashFlag()
            return null
        }

    fun clearPendingCrashFlag() {
        deleteFlagFile(PENDING_CRASH_FLAG, "pending crash flag cleared")
    }

    fun hasPendingCrash(): Boolean = pendingCrashLogFile != null

    // ========== pending Java crash flag ==========

    fun setPendingJavaCrashFlag(logFileName: String) {
        try {
            val dir = crashLogsDir() ?: return
            (dir / PENDING_JAVA_CRASH_FLAG).writeText(logFileName)
            WeLogger.d(TAG, "pending Java crash flag set: $logFileName")
        } catch (e: IOException) {
            WeLogger.e(TAG, "failed to set pending Java crash flag", e)
        }
    }

    val pendingJavaCrashLogFileName: String?
        get() = readFlagFile(PENDING_JAVA_CRASH_FLAG, "pending Java crash")

    val pendingJavaCrashLogFile: Path?
        get() {
            val fileName = pendingJavaCrashLogFileName ?: return null
            val dir = crashLogsDir() ?: return null
            val logFile = dir / fileName
            if (logFile.exists() && logFile.isRegularFile()) return logFile
            clearPendingJavaCrashFlag()
            return null
        }

    fun clearPendingJavaCrashFlag() {
        deleteFlagFile(PENDING_JAVA_CRASH_FLAG, "pending Java crash flag cleared")
    }

    fun hasPendingJavaCrash(): Boolean = pendingJavaCrashLogFile != null

    val crashLogDirPath: String get() = crashLogsDir()?.absolutePathString() ?: ""

    /**
     * 每个崩溃报告都必须带上的文件名前缀——[allCrashLogs] 依此过滤。
     * 提供给 native handler 安装时统一命名；本项目 native 崩溃不移植，仅供 UI/诊断参考。
     */
    val crashLogFileNamePrefix: String get() = CRASH_LOGS_PREFIX

    private fun readFlagFile(flagFileName: String, logLabel: String): String? {
        return try {
            val dir = crashLogsDir() ?: return null
            val flagFile = dir / flagFileName
            if (!flagFile.exists()) return null
            val fileName = flagFile.readText().trim()
            WeLogger.d(TAG, "pending $logLabel log: $fileName")
            fileName
        } catch (e: IOException) {
            WeLogger.e(TAG, "failed to get $logLabel flag", e)
            null
        }
    }

    private fun deleteFlagFile(flagFileName: String, logMessage: String) {
        val dir = crashLogsDir() ?: return
        val flagFile = dir / flagFileName
        if (flagFile.exists() && runCatching { flagFile.deleteExisting() }.isSuccess) {
            WeLogger.d(TAG, logMessage)
        }
    }
}
