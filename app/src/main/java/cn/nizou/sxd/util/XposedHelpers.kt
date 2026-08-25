package cn.nizou.sxd.util

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 临时迁移兼容层：等价旧 `de.robv.android.xposed.XposedHelpers` 常用方法的本地反射实现。
 *
 * 现代 libxposed API 官方已移除框架注入的 XposedHelpers（参考 LSPosed Wiki / 记忆 03）。
 * 本门面基于 java.lang.reflect，随模块打包进 APK，运行时无需框架提供。
 * 后续可逐步替换为显式反射或 libxposed 的 Invoker。
 */
object XposedHelpers {

    fun findClass(className: String, classLoader: ClassLoader): Class<*> =
        Class.forName(className, false, classLoader)

    fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? =
        try {
            findClass(className, classLoader)
        } catch (_: Throwable) {
            null
        }

    // ---- 字段 ----
    // 说明：obj 参数声明为可空（Any?）以兼容从 List<*> 等取出的元素；运行时调用方保证非空，
    // 内部仍按其 javaClass 反射。与旧 de.robv.android.xposed.XposedHelpers 的宽松签名保持一致。

    fun getObjectField(obj: Any?, name: String): Any? {
        val o = obj ?: return null
        return fieldOf(o.javaClass, name).also { it.isAccessible = true }.get(o)
    }

    fun getStaticObjectField(clazz: Class<*>, name: String): Any? =
        clazz.getDeclaredField(name).also { it.isAccessible = true }.get(null)

    fun setObjectField(obj: Any?, name: String, value: Any?) {
        val o = obj ?: return
        fieldOf(o.javaClass, name).also { it.isAccessible = true }.set(o, value)
    }

    fun setBooleanField(obj: Any?, name: String, value: Boolean) {
        val o = obj ?: return
        fieldOf(o.javaClass, name).also { it.isAccessible = true }.setBoolean(o, value)
    }

    fun getIntField(obj: Any?, name: String): Int {
        val o = obj ?: return 0
        return fieldOf(o.javaClass, name).also { it.isAccessible = true }.getInt(o)
    }

    fun setIntField(obj: Any?, name: String, value: Int) {
        val o = obj ?: return
        fieldOf(o.javaClass, name).also { it.isAccessible = true }.setInt(o, value)
    }

    fun setLongField(obj: Any?, name: String, value: Long) {
        val o = obj ?: return
        fieldOf(o.javaClass, name).also { it.isAccessible = true }.setLong(o, value)
    }

    // ---- 方法 ----

    fun callMethod(obj: Any?, name: String, vararg args: Any?): Any? {
        val o = obj ?: return null
        return methodOf(o.javaClass, name, args).also { it.isAccessible = true }.invoke(o, *args)
    }

    fun callStaticMethod(clazz: Class<*>, name: String, vararg args: Any?): Any? =
        methodOf(clazz, name, args).also { it.isAccessible = true }.invoke(null, *args)

    // ---- 内部 ----

    private fun fieldOf(clazz: Class<*>, name: String): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldException("$name in $clazz")
    }

    private fun methodOf(clazz: Class<*>, name: String, args: Array<out Any?>): Method {
        val argClasses = args.map { it?.javaClass }
        var c: Class<*>? = clazz
        while (c != null) {
            for (m in c.declaredMethods) {
                if (m.name != name || m.parameterCount != args.size) continue
                val pts = m.parameterTypes
                var ok = true
                for (i in pts.indices) {
                    val argCls = argClasses[i] ?: continue // null 匹配任意引用类型
                    if (!pts[i].isAssignableFrom(argCls)) {
                        ok = false
                        break
                    }
                }
                if (ok) return m
            }
            c = c.superclass
        }
        throw NoSuchMethodException("$name(${args.size} args) in $clazz")
    }
}
