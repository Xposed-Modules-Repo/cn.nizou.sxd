package cn.nizou.sxd.hook

import cn.nizou.sxd.PATTERN_NICKNAME
import cn.nizou.sxd.util.Common
import io.github.libxposed.api.XposedInterface
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * 昵称规则放开 + 昵称长度翻倍。
 *
 * **2026-08-29 LSPosed 适配（重要）**：本 hook 对 `java.lang.String.getBytes(Charset)` 的
 * 拦截在 **LSPosed(standard)** 下会引发宿主导线程**无限递归**（真机 ANR "Process failed to
 * complete startup"，堆 512MB OOM）——npatch 下正常、LSPosed 下崩，根因是 LSPosed 的
 * `chain.proceed()` / hook 链在序列化参数、构造 toString、打异常栈时会**再次调用
 * `String.getBytes(Charset)`**，于是每次递归又进本 hooker，形成死循环。
 *
 * **保留功能 + 防递归的通用解法：同线程重入守卫**。
 * - 第一次进入（真实业务调用）→ 照常 `chain.proceed()`，若 charset==GBK 且开了翻倍开关，
 *   则把返回字节数组长度减半（实现昵称字节数翻倍）。
 * - 重入进入（hook 链内部再次调 getBytes）→ 同一线程标记已在该 hook 内，直接 `chain.proceed()`
 *   原样放行，不再翻倍、也不再触发新的翻倍分支，从而**打断递归**（深度恒为 1）。
 * - 用 ThreadLocal 标记，只挡住当前线程的重入，不影响其它线程并行调用 getBytes 的真实翻倍。
 *
 * 这样既保住 npatch 上可用、用户依赖的「昵称长度翻倍」功能，又根治 LSPosed 下的无限递归崩退。
 */
class NicknameHook(
    self: XposedInterface,
    classLoader: ClassLoader
) : BaseHook(self, classLoader) {

    override val name: String
        get() = "NicknameHook"

    override fun startHook() {
        Pattern::class.java.findConstructor(
            String::class.java,
            Int::class.javaPrimitiveType!!
        ).intercept("nickname_pattern") { chain ->
            if (Common.removeRestrictionOnNickname && chain.getArg(0) == PATTERN_NICKNAME) {
                val args = chain.args.toTypedArray()
                args[0] = "[\\S]*"
                return@intercept chain.proceed(args)
            }
            chain.proceed()
        }

        val gbk = Charset.forName("GBK")
        String::class.java.findMethod("getBytes", Charset::class.java)
            .intercept("nickname_getbytes") { chain ->
                // 同线程重入守卫：已在 hook 内（多为 LSPosed hook 链自身再触发 getBytes）
                // 则原样放行，避免无限递归，同时保留真实业务调用的翻倍。
                if (inGetBytes.get()) {
                    return@intercept chain.proceed()
                }
                inGetBytes.set(true)
                try {
                    val result = chain.proceed()
                    if (!Common.doubleNicknameLength || chain.getArg(0) != gbk) {
                        return@intercept result
                    }
                    (result as? ByteArray)?.let { return@intercept it.copyOf(it.size / 2) }
                    result
                } finally {
                    inGetBytes.set(false)
                }
            }
    }

    /** 每线程重入标记，避免 LSPosed hook 链在 proceed 内部再调 getBytes 时无限递归。 */
    private val inGetBytes = ThreadLocal.withInitial { false }
}
