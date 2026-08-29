package cn.nizou.sxd.hook

import cn.nizou.sxd.PATTERN_NICKNAME
import cn.nizou.sxd.util.Common
import io.github.libxposed.api.XposedInterface
import java.util.regex.Pattern

/**
 * 昵称规则放开 + 昵称长度翻倍。
 *
 * **2026-08-29 LSPosed 适配（重要）**：对 `java.lang.String.getBytes(Charset)` 的 hook 在
 * **LSPosed(standard)** 下会引发宿主导线程**无限递归**（真机 ANR "Process failed to complete
 * startup"，主线程 getBytes→printStackTrace 死循环，堆 512MB OOM）——npatch 的 hook 链内部
 * 不再调 getBytes，而 LSPosed 的 hook 桥在序列化/toString/打异常栈时会再次调 getBytes，每次
 * 递归又进本 hooker，无论如何写法（chain.proceed / getInvoker(ORIGIN)）都无法打断。
 *
 * 因此本实现**最小化 hook 面**：只保留 Pattern 构造器 hook（放开昵称字符限制，非递归源）；
 * **禁用 getBytes(Charset) hook**（昵称长度翻倍功能）以根治 LSPosed 崩退。
 *
 * ⚠️ 若必须保留「昵称长度翻倍」，需换 hook 点（宿主昵称编码/上传的具体方法），见交接记忆 13。
 * 当前策略：优先确保宿主不崩，昵称长度翻倍暂缓（用户可先关闭该开关；npatch 下仍可用）。
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
