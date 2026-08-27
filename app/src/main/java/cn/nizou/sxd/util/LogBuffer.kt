package cn.nizou.sxd.util

import java.util.ArrayDeque

/**
 * 内存环形日志缓冲（单例）。[logI] 每写一条日志就 push 进来，实时日志悬浮窗每帧拉取
 * [snapshot] / [snapshotText] 渲染。容量封顶 [CAPACITY]，超过后丢弃最旧。
 * 线程安全：写入来自宿主/模块任意线程，读取来自悬浮窗主线程刷新，统一加锁。
 */
object LogBuffer {

    const val CAPACITY = 500

    private val logs = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        logs.addLast(line)
        while (logs.size > CAPACITY) {
            logs.removeFirst()
        }
    }

    @Synchronized
    fun snapshot(): List<String> = logs.toList()

    @Synchronized
    fun snapshotText(): String {
        if (logs.isEmpty()) return "(暂无日志)"
        val sb = StringBuilder()
        for (line in logs) {
            sb.append(line).append('\n')
        }
        return sb.toString()
    }

    @Synchronized
    fun clear() = logs.clear()
}
