package cn.nizou.sxd.util.crash

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import cn.nizou.sxd.BuildConfig
import cn.nizou.sxd.util.WeLogger
import java.io.BufferedReader
import java.io.FileReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃信息收集工具类
 * 负责收集设备信息、应用信息、线程信息、堆栈信息等
 *
 * 移植自 wekit utils/crash/CrashInfoCollector.kt；native 崩溃收集不移植。
 * 差异点：
 * - getThreadId 不再用 polyfills，直接用 `Thread.currentThread().id`
 * - 宿主进程早期崩溃时 Application 可能取不到，context 允许为 null，相关块自动降级
 * - 内部日志走 t1 的 [WeLogger]
 */
object CrashInfoCollector {

    private const val TAG = "CrashInfoCollector"

    /**
     * 收集完整的崩溃信息
     *
     * @param context   上下文（可为 null：仅影响应用信息/内存信息两块）
     * @param throwable 异常对象
     * @param crashType 崩溃类型 (JAVA/NATIVE)
     * @return 格式化的崩溃信息
     */
    fun collectCrashInfo(context: Context?, throwable: Throwable, crashType: String): String {
        val sb = "========================================\n" +
                "AutoOral Calculation Crash Report\n" +
                "========================================\n\n" +
                "Crash Time: " + currentTime + "\n" +
                "Crash Type: " + crashType + "\n\n" +
                "========================================\n" +
                "Device Information\n" +
                "========================================\n" +
                collectDeviceInfo() + "\n" +
                "========================================\n" +
                "Application Information\n" +
                "========================================\n" +
                collectAppInfo(context) + "\n" +
                "========================================\n" +
                "Module Information\n" +
                "========================================\n" +
                collectModuleInfo() + "\n" +
                "========================================\n" +
                "Memory Information\n" +
                "========================================\n" +
                collectMemoryInfo(context) + "\n" +
                "========================================\n" +
                "Thread Information\n" +
                "========================================\n" +
                collectThreadInfo() + "\n" +
                "========================================\n" +
                "Exception Stack Trace\n" +
                "========================================\n" +
                getStackTraceString(throwable) + "\n" +
                "========================================\n" +
                "All Threads Stack Trace\n" +
                "========================================\n" +
                collectAllThreadsStackTrace() + "\n" +
                "========================================\n" +
                "End of Crash Report\n" +
                "========================================\n"

        return sb
    }

    private val currentTime: String
        /**
         * 获取当前时间
         */
        get() {
            val sdf = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()
            )
            return sdf.format(Date())
        }

    /**
     * 收集设备信息
     */
    private fun collectDeviceInfo(): String {
        val sb = StringBuilder()
        sb.append("Brand: ").append(Build.BRAND).append("\n")
        sb.append("Model: ").append(Build.MODEL).append("\n")
        sb.append("Device: ").append(Build.DEVICE).append("\n")
        sb.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
        sb.append("Product: ").append(Build.PRODUCT).append("\n")
        sb.append("Hardware: ").append(Build.HARDWARE).append("\n")
        sb.append("Board: ").append(Build.BOARD).append("\n")
        sb.append("Display: ").append(Build.DISPLAY).append("\n")
        sb.append("Fingerprint: ").append(Build.FINGERPRINT).append("\n")
        sb.append("Android Version: ").append(Build.VERSION.RELEASE).append("\n")
        sb.append("SDK Version: ").append(Build.VERSION.SDK_INT).append("\n")
        sb.append("Supported ABIs: ")
        for (abi in Build.SUPPORTED_ABIS) {
            sb.append(abi).append(" ")
        }
        sb.append("\n")
        return sb.toString()
    }

    /**
     * 收集应用信息（宿主应用；context 为 null 时跳过包信息，PID/TID 仍可收集）
     */
    private fun collectAppInfo(context: Context?): String {
        val sb = StringBuilder()
        try {
            if (context != null) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
                sb.append("Package Name: ").append(packageInfo.packageName).append("\n")
                sb.append("Version Name: ").append(packageInfo.versionName).append("\n")
                sb.append("Version Code: ").append(packageInfo.longVersionCode).append("\n")
            } else {
                sb.append("Package Info: unavailable (context is null)\n")
            }
            sb.append("Process ID: ").append(Process.myPid()).append("\n")
            sb.append("Thread ID: ").append(Thread.currentThread().id).append("\n")
            sb.append("Process Name: ").append(getProcessName(context)).append("\n")
        } catch (e: Exception) {
            sb.append("Failed to collect app info: ").append(e.message).append("\n")
        }
        return sb.toString()
    }

    /**
     * 收集模块信息（AutoOralCalculation 自身版本）
     */
    private fun collectModuleInfo(): String {
        return buildString {
            appendLine("Version Name: ${BuildConfig.VERSION_NAME}")
            appendLine("Version Code: ${BuildConfig.VERSION_CODE}")
            appendLine("Build Timestamp: ${BuildConfig.BUILD_TIMESTAMP}")
        }
    }

    /**
     * 收集内存信息（context 为 null 时跳过系统内存块）
     */
    private fun collectMemoryInfo(context: Context?): String {
        val sb = StringBuilder()
        try {
            // 应用内存信息
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            val totalMemory = runtime.totalMemory() / 1024 / 1024
            val freeMemory = runtime.freeMemory() / 1024 / 1024
            val usedMemory = totalMemory - freeMemory

            sb.append("Max Memory: ").append(maxMemory).append(" MB\n")
            sb.append("Total Memory: ").append(totalMemory).append(" MB\n")
            sb.append("Used Memory: ").append(usedMemory).append(" MB\n")
            sb.append("Free Memory: ").append(freeMemory).append(" MB\n")

            // Native 内存信息
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            sb.append("Native Heap Size: ").append(memoryInfo.nativePss).append(" KB\n")
            sb.append("Dalvik Heap Size: ").append(memoryInfo.dalvikPss).append(" KB\n")
            sb.append("Total PSS: ").append(memoryInfo.totalPss).append(" KB\n")

            // 系统内存信息
            if (context != null) {
                val activityManager = context.getSystemService(ActivityManager::class.java)
                if (activityManager != null) {
                    val systemMemInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(systemMemInfo)
                    sb.append("System Available Memory: ")
                        .append(systemMemInfo.availMem / 1024 / 1024).append(" MB\n")
                    sb.append("System Total Memory: ")
                        .append(systemMemInfo.totalMem / 1024 / 1024).append(" MB\n")
                    sb.append("System Low Memory: ").append(systemMemInfo.lowMemory).append("\n")
                } else {
                    sb.append("System Memory: unavailable (no ActivityManager)\n")
                }
            } else {
                sb.append("System Memory: unavailable (context is null)\n")
            }
        } catch (e: Exception) {
            sb.append("Failed to collect memory info: ").append(e.message).append("\n")
        }
        return sb.toString()
    }

    /**
     * 收集线程信息
     */
    private fun collectThreadInfo(): String {
        val sb = StringBuilder()
        try {
            val currentThread = Thread.currentThread()
            sb.append("Current Thread: ").append(currentThread.name).append("\n")
            sb.append("Thread ID: ").append(currentThread.id).append("\n")
            sb.append("Thread Priority: ").append(currentThread.priority).append("\n")
            sb.append("Thread State: ").append(currentThread.state).append("\n")
            sb.append("Thread Group: ").append(currentThread.threadGroup?.name ?: "null")
                .append("\n")

            // 活跃线程数
            var rootGroup = currentThread.threadGroup
            if (rootGroup != null) {
                while (rootGroup.parent != null) {
                    rootGroup = rootGroup.parent
                }
                sb.append("Active Thread Count: ").append(rootGroup.activeCount()).append("\n")
            } else {
                sb.append("Active Thread Count: unavailable\n")
            }
        } catch (e: Exception) {
            sb.append("Failed to collect thread info: ").append(e.message).append("\n")
        }
        return sb.toString()
    }

    /**
     * 获取异常堆栈字符串
     */
    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    /**
     * 收集所有线程的堆栈信息
     */
    private fun collectAllThreadsStackTrace(): String {
        val sb = StringBuilder()
        try {
            val allStackTraces = Thread.getAllStackTraces()
            sb.append("Total Threads: ").append(allStackTraces.size).append("\n\n")

            for (entry in allStackTraces.entries) {
                val thread = entry.key
                val stackTrace = entry.value

                sb.append("Thread: ").append(thread.name)
                    .append(" (ID: ").append(thread.id)
                    .append(", State: ").append(thread.state)
                    .append(", Priority: ").append(thread.priority)
                    .append(")\n")

                if (stackTrace.isNotEmpty()) {
                    for (element in stackTrace) {
                        sb.append("    at ").append(element.toString()).append("\n")
                    }
                } else {
                    sb.append("    (No stack trace available)\n")
                }
                sb.append("\n")
            }
        } catch (e: Exception) {
            sb.append("Failed to collect all threads stack trace: ").append(e.message).append("\n")
        }
        return sb.toString()
    }

    /**
     * 获取进程名称（先查 ActivityManager，失败则读 /proc/self/cmdline）
     */
    private fun getProcessName(context: Context?): String {
        try {
            if (context != null) {
                val pid = Process.myPid()
                val activityManager = context.getSystemService(ActivityManager::class.java)
                val runningApps = activityManager?.runningAppProcesses
                if (runningApps != null) {
                    for (processInfo in runningApps) {
                        if (processInfo.pid == pid) {
                            return processInfo.processName
                        }
                    }
                }
            }

            // 备用方法：读取 /proc/self/cmdline
            val processName = BufferedReader(FileReader("/proc/self/cmdline")).use { it.readLine() }
            if (processName != null) {
                val trimmed = processName.trim { it <= ' ' }
                if (trimmed.isNotEmpty()) {
                    return trimmed
                }
            }
        } catch (e: Exception) {
            WeLogger.e(TAG, "error while getting process name", e)
        }
        return "Unknown"
    }
}
