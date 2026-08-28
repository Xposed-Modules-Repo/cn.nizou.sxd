package cn.nizou.sxd.util

import android.graphics.PointF
import cn.nizou.sxd.XposedInit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * native libauto_oral 加载（笔迹识别）。
 *
 * **延迟加载**：System.load 与 Xposed 框架（尤其 LSPosed standard）的 native 加载窗口冲突会
 * 直接 abort 进程（JNI 错误无法用 runCatching 捕获）。因此不在启动/attach 时加载，而是在
 * 首次真正使用笔迹功能时才加载，避开框架初始化窗口；加载失败仅影响笔迹，不影响其它功能。
 */
private val nativeLoaded = AtomicBoolean(false)

private fun ensureNativeLoaded(): Boolean {
    if (nativeLoaded.get()) return true
    synchronized(nativeLoaded) {
        if (nativeLoaded.get()) return true
        return try {
            val self = XposedInit.self
            System.load(
                self.moduleApplicationInfo.nativeLibraryDir + File.separator + "libauto_oral.so"
            )
            nativeLoaded.set(true)
            true
        } catch (_: Throwable) {
            logI("libauto_oral load failed, strokes disabled")
            false
        }
    }
}

val String.nativeStrokes: List<Array<DoubleArray>>
    external get

val String.strokes: List<Array<PointF>> get() {
    if (!ensureNativeLoaded()) return emptyList()
    return nativeStrokes.map {
        it.map { PointF(it[0].toFloat(), it[1].toFloat()) }.toTypedArray()
    }.also {
        logI("answer: $this, strokes: ${it.size}")
    }
}

val String.pathPoints get(): List<Array<DoubleArray>> {
    if (!ensureNativeLoaded()) return emptyList()
    return nativeStrokes
}

fun List<Array<*>>.toJsonString(): String {
    return toJSONArray().toString()
}

fun List<Array<*>>.toJSONArray(): JSONArray {
    val jsonArray = JSONArray()
    forEach {
        val arr = JSONArray()
        it.forEach { point ->
            val p = JSONObject()
            when (point) {
                is PointF -> {
                    p.put("x", point.x)
                    p.put("y", point.y)
                }
                is DoubleArray -> {
                    p.put("x", point[0])
                    p.put("y", point[1])
                }
                else -> throw UnsupportedOperationException()
            }
            arr.put(p)
        }
        jsonArray.put(arr)
    }
    return jsonArray
}

