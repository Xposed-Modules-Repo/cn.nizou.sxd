package cn.nizou.sxd.util

import android.util.Log
import cn.nizou.sxd.XposedInit

private const val TAG = "AutoOral"

fun logI(vararg infos: Any) {
    val self = try {
        XposedInit.self
    } catch (_: UninitializedPropertyAccessException) {
        null
    }
    infos.forEach {
        val throwable = it as? Throwable
        self?.log(Log.INFO, TAG, "$TAG >>> $it", throwable)
        Log.e(TAG, it.toString())
        if (throwable != null) {
            Log.e(TAG, "", throwable)
        }
        // 写入内存环形缓冲，供实时日志悬浮窗渲染
        val text = if (throwable != null) "${it} :: ${throwable.message}" else it.toString()
        LogBuffer.add(text)
        // 写入文件（异步 WeLogger，供 LogsScreen 查看）：tag 统一 AutoOral；
        // Throwable 走 E 级带完整堆栈，普通消息走 I 级（logI 语义）
        if (throwable != null) WeLogger.e(TAG, text, throwable) else WeLogger.i(TAG, text)
    }
}
