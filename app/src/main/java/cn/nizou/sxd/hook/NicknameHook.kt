package cn.nizou.sxd.hook

import cn.nizou.sxd.PATTERN_NICKNAME
import cn.nizou.sxd.util.Common
import io.github.libxposed.api.XposedInterface
import java.util.regex.Pattern

/**
 * 昵称规则放开。
 *
 * **2026-08-29 重大安全修复**：删除了对 `java.lang.String.getBytes(Charset)` 的 hook。
 * 该 hook 在 LSPosed(standard) 下必然引起宿主导线程**无限递归**——String.getBytes 是超热路径，
 * LSPosed 框架在 chain.proceed() 时（序列化 hooker 参数/异常/日志，或原始 getBytes 内部 toString）
 * 会再次调用 getBytes(Charset)，使 hooker 反复触发，堆被顶到 512MB 触发 OOM，启动超时被系统杀
 * （真机 logcat 表现为 ANR "Process failed to complete startup"，用户视角即「打开就闪退」）。
 *
 * 隔离实验：prefs 把 doubleNicknameLength 置 false 后递归依旧（hook 本身仍拦截所有 getBytes），
 * 仅延长存活 ~12s→~21s，根因未除。因此必须移除该 hook，而不是关开关。
 *
 * 仍保留 Pattern 构造器 hook（放开昵称字符限制），此 hook 非递归源，且 removeRestrictionOnNickname
 * 默认 false，安全。昵称「长度翻倍」功能（原 getBytes 方案）已一并移除：不存在不递归的安全替代做法。
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
    }
}