package cn.nizou.sxd.util

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.concurrent.ConcurrentHashMap

/**
 * DexKit 定位器（版本适配兜底层，高手方案——见 skill 05 §8）。
 *
 * 场景：类名被混淆（Classname 常量失效）或方法签名不唯一时，用 DexKit 在宿主 dex 中
 * 按「方法参数类型 / 返回类型 / 字符串引用」模糊搜索类与方法，替代每个版本手工逆向硬编码。
 *
 * 注意：
 * - DexKit 遍历 dex 索引很快（毫秒~百毫秒级），但**首次搜索会建索引**，必须在后台线程跑，
 *   结果缓存到 [cache]，后续同步命中。
 * - `searchMethod` 返回形如 `Lcom/xxx/a;->e(Ljava/lang/String;Ljava/util/List;)V` 的
 *   方法描述符集合，取第一个的类名即目标类。
 */
object DexKitLocator {

    private val cache = ConcurrentHashMap<String, Set<String>>()

    @Volatile
    private var bridge: DexKitBridge? = null

    /** 用宿主 classLoader 初始化（幂等；线程安全）。失败返回 null 不抛。 */
    fun init(classLoader: ClassLoader): Boolean {
        if (bridge != null) return true
        return runCatching {
            val b = DexKitBridge.create(classLoader) ?: return false
            bridge = b
            true
        }.getOrDefault(false)
    }

    /** 释放 DexKit（宿主进程结束时调用，可选）。 */
    fun close() {
        runCatching { bridge?.close() }.onFailure { logI(it) }
        bridge = null
        cache.clear()
    }

    /**
     * 按方法签名特征搜索类名集合（缓存命中直接返回）。
     *
     * @param paramTypeNames 参数类型名数组（dex 描述符简写，如 "java.lang.String"、"java.util.List"）；
     *   传空数组表示不限参数（需配合 [returnTypeName]/[usingStrings] 缩小范围）
     * @param returnTypeName 返回类型名（可为 null）
     * @param usingStrings 方法体内引用的字符串（如 URL 路径、字段名），全部命中才算
     * @return 匹配类名集合（如 "com.fenbi.android.leo.exercise.math.quick.QuickExercisePresenter"）
     */
    fun findClassNamesByMethod(
        paramTypeNames: List<String>,
        returnTypeName: String? = null,
        usingStrings: List<String> = emptyList(),
    ): Set<String> {
        val key = listOf(paramTypeNames, returnTypeName ?: "", usingStrings).toString()
        cache[key]?.let { return it }

        val b = bridge ?: return emptySet()
        return runCatching {
            val methods = b.searchMethod {
                matcher {
                    if (paramTypeNames.isNotEmpty()) {
                        paramTypes = paramTypeNames.toTypedArray()
                    }
                    if (returnTypeName != null) {
                        returnType = returnTypeName
                    }
                    if (usingStrings.isNotEmpty()) {
                        usingStrings(usingStrings) {
                            stringMatchType = StringMatchType.EQUALS
                        }
                    }
                }
            }
            val classNames = methods.mapNotNull { desc ->
                // desc 形如 "Lcom/xxx/a;->e(Ljava/lang/String;...)V" → 取类名
                desc.substringAfter("L").substringBefore(";")
                    .replace('/', '.')
            }.toSet()
            cache[key] = classNames
            logI("DexKitLocator: key=$key → ${classNames.size} classes, first=${classNames.firstOrNull()}")
            classNames
        }.getOrElse {
            logI("DexKitLocator search failed: ${it.message}")
            emptySet()
        }
    }

    /** 便捷：参数类型名数组直接定位唯一类名；不唯一/失败返回 null。 */
    fun findClassName(
        paramTypeNames: List<String>,
        returnTypeName: String? = null,
        usingStrings: List<String> = emptyList(),
    ): String? {
        val names = findClassNamesByMethod(paramTypeNames, returnTypeName, usingStrings)
        return if (names.size == 1) names.first() else null
    }
}
