package cn.nizou.sxd.hook

import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.logI
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * 所有 hook 的抽象基类（API 102）。
 *
 * 现代 API 的目标类查找必须使用宿主 ClassLoader；hook() 必须经由 XposedModule(self)。
 * before/after 语义改为 OkHttp 式 Chain：见各子类中的 `self.hook(...).intercept { chain -> }`。
 */
abstract class BaseHook(
    protected val self: XposedInterface,
    protected val classLoader: ClassLoader
) {

    abstract val name: String

    fun findClass(className: String): Class<*> = XposedHelpers.findClass(className, classLoader)

    protected fun Class<*>.allMethod(name: String): List<Method> =
        declaredMethods.filter { it.name == name }

    protected fun Class<*>.findMethod(name: String, vararg parameterTypes: Class<*>): Method =
        getDeclaredMethod(name, *parameterTypes).also { it.isAccessible = true }

    protected fun Class<*>.findConstructor(vararg parameterTypes: Class<*>): Constructor<*> =
        getDeclaredConstructor(*parameterTypes).also { it.isAccessible = true }

    /**
     * 用现代 hook 链注册拦截器。旧 before/after 落 Chain 的语义见记忆 03 §3.2：
     * - 改参：复制 chain.args 后 `chain.proceed(newArgs)`
     * - 短路/改结果：直接返回目标值（不 proceed 即短路）
     * - 观测：`return chain.proceed()`
     *
     * 调用方式：`method.intercept("id") { chain -> ... }`（尾随 lambda 经 SAM 转成 Hooker）。
     */
    protected fun Executable.intercept(
        id: String = name,
        hooker: XposedInterface.Hooker
    ): HookHandle = when (this) {
        is Method -> self.hook(this).setId(id).setExceptionMode(ExceptionMode.DEFAULT).intercept(hooker)
        is Constructor<*> -> self.hook(this).setId(id).setExceptionMode(ExceptionMode.DEFAULT).intercept(hooker)
        else -> throw IllegalArgumentException("unexpected executable: $this")
    }

    fun startHookCatching(): Result<Unit> {
        return kotlin.runCatching {
            startHook()
        }.onFailure {
            logI("failure in $name >>>>>>")
            logI(it)
            logI("failure in $name <<<<<<")
        }
    }

    protected abstract fun startHook()

    companion object {
        fun startHook(self: XposedInterface, classLoader: ClassLoader) {
            listOf(
                PracticeHook(self, classLoader),
                RecognizerHook(self, classLoader),
                WebViewHook(self, classLoader),
                SettingHook(self, classLoader),
                RetrofitHook(self, classLoader),
                NicknameHook(self, classLoader),
                SimianHook(self, classLoader),
            ).forEach { it.startHookCatching() }
        }
    }
}
