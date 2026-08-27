package cn.nizou.sxd

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import cn.nizou.sxd.hook.BaseHook
import cn.nizou.sxd.util.HookStatus
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
        HookStatus.markFrameworkLoaded()
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

        BaseHook.startHook(this, param.classLoader)
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
