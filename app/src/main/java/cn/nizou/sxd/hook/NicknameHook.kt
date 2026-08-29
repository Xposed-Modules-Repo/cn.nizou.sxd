package cn.nizou.sxd.hook

import cn.nizou.sxd.PATTERN_NICKNAME
import cn.nizou.sxd.util.Common
import cn.nizou.sxd.util.logI
import io.github.libxposed.api.XposedInterface
import java.util.regex.Pattern

/**
 * 昵称规则放开 + 昵称长度限制无视（合并为「无视名字限制」开关）。
 *
 * **2026-08-29 LSPosed 适配（重要）**：对 `java.lang.String.getBytes(Charset)` 的 hook 在
 * **LSPosed(standard)** 下会引发宿主导线程**无限递归**（真机 ANR "Process failed to complete
 * startup"，主线程 getBytes→printStackTrace 死循环，堆 512MB OOM）——npatch 的 hook 链内部
 * 不再调 getBytes，而 LSPosed 的 hook 桥在序列化/toString/打异常栈时会再次调 getBytes，每次
 * 递归又进本 hooker，无论如何写法（chain.proceed / getInvoker(ORIGIN)）都无法打断。
 *
 * **安全实现（2026-08-29 定案）**：宿主 `leo-user-info` 的昵称校验器
 * `cs.p7.c(nick)`：GBK 字节长度（`cs.p7.a(String):Int`，`nick.getBytes(GBK).length`）≤ 16 字节，
 * 超长报「昵称长度不合法」；字符格式另有 `jv.j.b` 校验。直接 hook **`cs.p7.a`**（非框架方法、
 * 仅昵称校验调用、非热路径、无 LSPosed 递归风险），开关开启时返回 `0` → `len > 16` 永不成立，
 * 任意长度昵称都通过；字符/格式限制由 Pattern 构造器 hook 放开。两者由单一开关
 * 「无视名字限制」（[Common.ignoreNicknameRestriction]）合并控制。
 * `cs.p7.a` 全宿主仅被 `c(nick)` 调用，不影响其它校验。
 */
class NicknameHook(
    self: XposedInterface,
    classLoader: ClassLoader
) : BaseHook(self, classLoader) {

    override val name: String
        get() = "NicknameHook"

    override fun startHook() {
        // 字符/格式限制放开：昵称格式 Pattern 构造时替换为「任意非空白字符」。
        Pattern::class.java.findConstructor(
            String::class.java,
            Int::class.javaPrimitiveType!!
        ).intercept("nickname_pattern") { chain ->
            if (Common.ignoreNicknameRestriction && chain.getArg(0) == PATTERN_NICKNAME) {
                val args = chain.args.toTypedArray()
                args[0] = "[\\S]*"
                return@intercept chain.proceed(args)
            }
            chain.proceed()
        }

        // 长度限制无视：宿主 GBK 字节长度计算 cs.p7.a(String):Int 返回值置 0，
        // 版本不存在/方法漂移时容错跳过（findClass 失败不影响其它 hook）。
        runCatching {
            findClass("cs.p7").findMethod("a", String::class.java)
                .intercept("nickname_gbk_len") { chain ->
                    val len = chain.proceed() as? Int ?: 0
                    if (Common.ignoreNicknameRestriction) 0 else len
                }
        }.onFailure { logI("nickname gbk-len hook skipped: ${it.message}") }
    }
}
