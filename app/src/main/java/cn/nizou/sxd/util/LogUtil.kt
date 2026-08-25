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
    }
}
