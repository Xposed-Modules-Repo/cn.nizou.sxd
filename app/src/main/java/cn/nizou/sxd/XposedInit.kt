package cn.nizou.sxd

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import cn.nizou.sxd.MODULE_PREFS_NAME
import cn.nizou.sxd.hook.BaseHook
import cn.nizou.sxd.util.HookStatus
import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.install
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * libxposed API 102 入口。继承 XposedModule，框架自动 attachFramework；
 * 不要在任何生命周期回调之前查找宿主类。宿主类一律用 param.classLoader 加载。
 */
class XposedInit : XposedModule() {

    companion object {
        /** 暴露给 util/hook 层做 hook()/getInvoker()/log() */
        lateinit var self: XposedInit
        lateinit var modulePath: String
        lateinit var moduleRes: Resources
    }

    @SuppressLint("DiscouragedApi")
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        self = this
        modulePath = moduleApplicationInfo.sourceDir
        moduleRes = createModuleResources(modulePath)
        HookStatus.markLocalActive()
        log(
            Log.INFO, "AutoOral",
            "event=module_loaded process=${param.processName} api=${apiVersion} framework=${frameworkName}"
        )
    }

    /**
     * 宿主主进程注入入口。现代回调可能对进程中加载的多个包触发，
     * 因此必须同时过滤 package 与 process（等价旧 packageName == processName）。
     * libxposed 的 PackageReadyParam 无 processName 字段，用 isFirstPackage
     * （true 表示当前进程中该包首次就绪）做单次保护，等价旧「主进程」判断。
     */
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != HOST_PACKAGE_NAME) return
        if (!param.isFirstPackage) return

        install() // 加载 native libauto_oral（绝对路径）

        // 跨进程激活标记：hook 成功后写入 RemotePreferences，供模块设置页读取（问题 3 修复）。
        try {
            HookStatus.markActive(getRemotePreferences(MODULE_PREFS_NAME))
        } catch (_: Throwable) {
        }

        // 关键修复（问题 1）：onPackageReady 在宿主 classLoader 尚未完全就绪时即被触发
        // （npatch/LSPosed 在 createOrUpdateClassLoaderLocked 期间调用），此时 findClass
        // 宿主应用类会抛 ClassNotFoundException。故不在此同步 hook，改为 hook
        // Application.attach —— 它在宿主应用 classLoader 完整创建后才执行，届时再 hook。
        hookAfterAppAttach(param.classLoader)
    }

    private fun hookAfterAppAttach(appClassLoader: ClassLoader) {
        try {
            val appClass = XposedHelpers.findClass("android.app.Application", appClassLoader)
            val attach = appClass.getDeclaredMethod("attach", android.content.Context::class.java)
            attach.isAccessible = true
            hook(attach).setId("app_attach").intercept { chain ->
                val r = chain.proceed()
                try {
                    BaseHook.startHook(this, appClassLoader)
                } catch (e: Throwable) {
                    Log.e("AutoOral", "hook after attach failed", e)
                }
                r
            }
        } catch (e: Throwable) {
            Log.e("AutoOral", "hookAfterAppAttach setup failed, fallback direct", e)
            try {
                BaseHook.startHook(this, appClassLoader)
            } catch (e2: Throwable) {
                Log.e("AutoOral", "fallback hook failed", e2)
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    @Suppress("DEPRECATION")
    private fun createModuleResources(apkPath: String): Resources {
        // AssetManager() 构造在 compileSdk 34 下被隐藏（package-private），改经反射创建。
        val am = AssetManager::class.java.getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance() as AssetManager
        val addAssetPath = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            .apply { isAccessible = true }
        addAssetPath.invoke(am, apkPath) // 隐藏 API；将模块资源与 assets 载入宿主
        return Resources(
            am,
            Resources.getSystem().displayMetrics,
            Resources.getSystem().configuration
        )
    }
}
