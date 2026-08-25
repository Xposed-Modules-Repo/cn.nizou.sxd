package cn.nizou.sxd.hook

import cn.nizou.sxd.PATTERN_NICKNAME
import cn.nizou.sxd.util.Common
import io.github.libxposed.api.XposedInterface
import java.nio.charset.Charset
import java.util.regex.Pattern

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
                val result = chain.proceed()
                if (!Common.doubleNicknameLength || chain.getArg(0) != gbk) {
                    return@intercept result
                }
                (result as? ByteArray)?.let { return@intercept it.copyOf(it.size / 2) }
                result
            }
    }
}
