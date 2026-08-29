package cn.nizou.sxd.hook

import cn.nizou.sxd.PATTERN_NICKNAME
import cn.nizou.sxd.XposedInit
import cn.nizou.sxd.util.Common
import io.github.libxposed.api.XposedInterface
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * 昵称规则放开 + 昵称长度翻倍。
 *
 * **2026-08-29 LSPosed 救命修复**：`String.getBytes(Charset)` 在 LSPosed(standard) 下若用
 * `chain.proceed()` 调原始方法，会触发**无限递归**（真机 ANR "Process failed to complete
 * startup"，主线程 getBytes→printStackTrace 死循环，堆 512MB OOM）——npatch 的 hook 链
 * 内部不会再调 getBytes，而 LSPosed 的 `chain.proceed()` / hook 桥在序列化、toString、
 * 打异常栈时会再次调 getBytes，于是每次递归又进本 hooker。
 *
 * **根治：用 `self.getInvoker(method).setType(ORIGIN)` 直接调原始方法，完全绕过
 * LSPosed hook 链分派**（不再进 o0.callback/g2.intercept/k.proceed），从而打断递归。
 * 项目里 WebViewHook.invokeOriginal 正是用这一机制。本 hook 只做「观察 + 翻倍」，调用原始
 * 一律走 ORIGIN，绝不 `chain.proceed()`。
 *
 * 同时保留同线程重入守卫：不同线程并行调用 getBytes 时，仍只对真实业务调用翻倍。
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
        val getBytesMethod = String::class.java.findMethod("getBytes", Charset::class.java)
        // 用 ORIGIN invoker 调原始方法，绕过 LSPosed hook 链，避免 getBytes 递归。
        val original = XposedInit.self.getInvoker(getBytesMethod)
            .also { it.setType(XposedInterface.Invoker.Type.ORIGIN) }

        getBytesMethod.intercept("nickname_getbytes") { chain ->
            if (inGetBytes.get() == true) {
                // 重入：直接用 ORIGIN 调原始，绝不 chain.proceed()（避免再次进 hook 链递归）。
                return@intercept original.invoke(chain.thisObject, chain.getArg(0))
            }
            inGetBytes.set(true)
            try {
                val result = original.invoke(chain.thisObject, chain.getArg(0))
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

    /** 每线程重入标记：不同线程并行调用 getBytes 时，仅真实业务调用翻倍。 */
    private val inGetBytes: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
}