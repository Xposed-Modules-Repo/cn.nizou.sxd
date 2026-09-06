package cn.nizou.sxd.util

/**
 * Reads the host-process XposedInit instance without linking XposedModule into the
 * standalone module process. Kotlin companion properties are instance getters
 * (getSelf()), not public static fields; use the getter with a private-field fallback.
 */
fun readInjectedModuleSelf(): Any? = runCatching {
    val outer = Class.forName("cn.nizou.sxd.XposedInit")
    val companion = outer.getField("Companion").get(null) ?: return null
    runCatching {
        companion.javaClass.getMethod("getSelf").invoke(companion)
    }.getOrElse {
        companion.javaClass.getDeclaredField("self").apply { isAccessible = true }.get(companion)
    }
}.getOrNull()
