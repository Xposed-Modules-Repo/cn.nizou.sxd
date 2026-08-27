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
        LogBuffer.add(
            if (throwable != null) "${it} :: ${throwable.message}" else it.toString()
        )
    }
}
