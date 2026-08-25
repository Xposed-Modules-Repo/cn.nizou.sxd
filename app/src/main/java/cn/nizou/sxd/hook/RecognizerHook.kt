package cn.nizou.sxd.hook

import cn.nizou.sxd.Classname
import cn.nizou.sxd.util.Common
import io.github.libxposed.api.XposedInterface

class RecognizerHook(
    self: XposedInterface,
    classLoader: ClassLoader
) : BaseHook(self, classLoader) {

    override val name: String
        get() = "RecognizerHook"

    override fun startHook() {
        val mathScriptRecognizerClass = findClass(Classname.MATH_SCRIPT_RECOGNIZER)
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
