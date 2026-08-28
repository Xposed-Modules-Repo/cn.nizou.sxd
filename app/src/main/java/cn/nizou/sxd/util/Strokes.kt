package cn.nizou.sxd.util

import android.graphics.PointF
import cn.nizou.sxd.XposedInit
import io.github.libxposed.api.XposedModule
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

/**
 * native libauto_oral 加载（笔迹识别）。
 *
 * **延迟加载**：System.load 与 Xposed 框架（尤其 LSPosed standard）的 native 加载窗口冲突会
 * 直接 abort 进程（JNI 错误无法用 runCatching 捕获）。因此不在启动/attach 时加载，而是在
 * 首次真正使用笔迹功能时才加载，避开框架初始化窗口；加载失败仅影响笔迹，不影响其它功能。
 */
private val nativeLoaded = AtomicBoolean(false)

private val loadAttempted = AtomicBoolean(false)

/**
 * 3.140 适配（2026-08-29）：
 *  - 失败结果缓存：每进程只尝试一次，不再每局重复 System.load + 刷日志；
 *  - 现代 AGP 默认 extractNativeLibs=false，nativeLibraryDir 下没有解压的 .so，
 *    System.load 直接 FileNotFoundException；改为从模块 APK（lib/<abi>/）解压到模块 filesDir 再加载。
 * 说明：libauto_oral 是 Rust(jni crate) 笔迹路径生成库；即使加载失败，秒结算仍可工作——
 * 判题/提交的正确性由 WebViewHook.hookDataEncrypt（提交载荷改写）保证。
 */
private fun ensureNativeLoaded(): Boolean {
    if (nativeLoaded.get()) return true
    synchronized(nativeLoaded) {
        if (nativeLoaded.get()) return true
        if (loadAttempted.get()) return false
        loadAttempted.set(true)
        return try {
            val self = XposedInit.self
            var path: String? = File(self.moduleApplicationInfo.nativeLibraryDir, "libauto_oral.so")
                .takeIf { it.exists() }?.absolutePath
            if (path == null) {
                path = extractLibFromApk(self)?.absolutePath
            }
            if (path != null) {
                System.load(path)
                nativeLoaded.set(true)
                logI("libauto_oral loaded: " + path)
                true
            } else {
                logI("libauto_oral not found (nativeLibraryDir empty), strokes disabled")
                false
            }
        } catch (e: Throwable) {
            logI("libauto_oral load failed: " + e.message)
            false
        }
    }
}

/** extractNativeLibs=false 时从模块 APK 解压 .so 到模块 filesDir。 */
private fun extractLibFromApk(self: XposedModule): File? = runCatching {
    val apkFile = File(self.moduleApplicationInfo.sourceDir)
    val abi = self.moduleApplicationInfo.nativeLibraryDir
        .split(File.separator).lastOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: android.os.Build.SUPPORTED_ABIS.firstOrNull()
        ?: "arm64-v8a"
    val zip = ZipFile(apkFile)
    try {
        val entry = zip.getEntry("lib/" + abi + "/libauto_oral.so")
            ?: zip.getEntry("lib/arm64-v8a/libauto_oral.so")
            ?: return null
        val dir = File(self.moduleApplicationInfo.dataDir, "files/lib").apply { mkdirs() }
        val out = File(dir, "libauto_oral.so")
        zip.getInputStream(entry).use { input ->
            out.outputStream().use { it.write(input.readBytes()) }
        }
        logI("libauto_oral extracted from APK to " + out)
        out
    } finally {
        runCatching { zip.close() }
    }
}.getOrNull()

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

