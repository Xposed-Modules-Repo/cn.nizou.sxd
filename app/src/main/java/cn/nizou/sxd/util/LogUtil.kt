package cn.nizou.sxd.util

import android.util.Log

private const val TAG = "AutoOral"

fun logI(vararg infos: Any) {
    // 反射拿 XposedInit.self：XposedInit 继承 XposedModule（compileOnly 不打包），模块本体
    // 独立进程没有该类，直接引用会在类加载阶段抛 NoClassDefFoundError（Error，普通 catch 不住）
    // → 模块本体任何 logI 调用都会闪退。反射 forName 抛错被 catch Throwable 兜住 → null。
    val self = try {
        val companion = Class.forName("cn.nizou.sxd.XposedInit\$Companion")
        companion.getField("self").get(null)
    } catch (_: Throwable) {
        null
    }
    infos.forEach {
        val throwable = it as? Throwable
        if (self != null) {
            try {
                val logMethod = self.javaClass.getMethod(
                    "log",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java,
                    Throwable::class.java
                )
                logMethod.invoke(self, Log.INFO, TAG, "$TAG >>> $it", throwable)
            } catch (_: Throwable) {
                // 框架日志失败不阻塞（fallback 到 Log.e）
            }
        }
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
