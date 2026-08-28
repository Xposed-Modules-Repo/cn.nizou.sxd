package cn.nizou.sxd.hook

import cn.nizou.sxd.Classname
import cn.nizou.sxd.util.Common
import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.logI
import io.github.libxposed.api.XposedInterface

class RecognizerHook(
    self: XposedInterface,
    classLoader: ClassLoader
) : BaseHook(self, classLoader) {

    override val name: String
        get() = "RecognizerHook"

    override fun startHook() {
        // 新版(3.140+) 已移除 MathScriptRecognizer（识别逻辑重构），类不存在时优雅跳过。
        val mathScriptRecognizerClass =
            XposedHelpers.findClassIfExists(Classname.MATH_SCRIPT_RECOGNIZER, classLoader)
                ?: return logI("RecognizerHook: MathScriptRecognizer not found in host, skip")
        mathScriptRecognizerClass.findMethod(
            "a",
            Int::class.javaPrimitiveType!!,
            List::class.java,
            List::class.java
        ).intercept("recognizer_a") { chain ->
            if (!Common.alwaysTrue) {
                return@intercept chain.proceed()
            }
            val answers = chain.getArg(2) as? List<*> ?: return@intercept chain.proceed()
            if (answers.isNotEmpty()) answers[0].toString() else ""
        }
    }
}
