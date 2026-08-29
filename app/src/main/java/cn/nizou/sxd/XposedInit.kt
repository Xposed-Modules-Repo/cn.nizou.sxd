package cn.nizou.sxd

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import cn.nizou.sxd.HOST_PACKAGE_NAME
import cn.nizou.sxd.MODULE_PREFS_NAME
import cn.nizou.sxd.hook.BaseHook
import cn.nizou.sxd.util.HookStatus
import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.crash.JavaCrashHandler
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
        // 崩溃捕获：onModuleLoaded 在每个被注入进程（宿主主/子进程）都会触发，统一安装；
        // 模块独立进程由 MainActivity 补装。失败不影响模块启动。
        runCatching { JavaCrashHandler.install() }
            .onFailure { Log.e("AutoOral", "JavaCrashHandler.install failed", it) }
        // 资源加载必须容错：部分框架版本（如 LSPosed standard）下反射 AssetManager/addAssetPath
        // 可能受隐藏 API 限制抛异常，导致模块加载失败/宿主闪退。失败时回退 Resources.getSystem()，
        // 保证 moduleRes 非空（StringRes 依赖它），注入面板仍可渲染。
        moduleRes = try {
            createModuleResources(modulePath)
        } catch (e: Throwable) {
            Log.e("AutoOral", "createModuleResources failed, fallback system resources", e)
            Resources.getSystem()
        }
        // 激活标记：只在宿主进程 onModuleLoaded 时置位本进程标记（作为 RemotePreferences 失败时的兜底）。
        // 模块自身进程（独立设置页/主界面）不应误置 true，否则激活卡片永远显绿。
        if (param.processName == HOST_PACKAGE_NAME) {
            HookStatus.markLocalActive()
        }
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
     *
     * 注入策略：hook `Application.attach` —— 它在宿主应用 classLoader 完整创建后才执行，
     * 此时 findClass 宿主类必然成功（npatch 与 LSPosed standard 均适用）。此前在
     * onPackageReady 直接 hook 会因部分框架版本 classLoader 未就绪而静默失败（异常被
     * BaseHook.startHookCatching 吞掉），故统一走 attach hook。native 库（libauto_oral）
     * 改为首次使用时懒加载（见 util/Strokes.kt），避免 System.load 与框架 native 加载
     * 窗口冲突 abort 进程。
     */
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != HOST_PACKAGE_NAME) return
        if (!param.isFirstPackage) return

        val appClassLoader = param.classLoader
        try {
            val appClass = XposedHelpers.findClass("android.app.Application", appClassLoader)
            val attach = appClass.getDeclaredMethod("attach", android.content.Context::class.java)
            attach.isAccessible = true
            hook(attach).setId("app_attach").intercept { chain ->
                val r = chain.proceed()
                try {
                    BaseHook.startHook(this, appClassLoader)
                    // 宿主注入成功后写入激活标记（此时 getRemotePreferences 已就绪）：
                    // 供模块设置页/注入面板读取。写入此位置可避免 onPackageReady 早期 RemotePreferences
                    // 未就绪导致写入被静默吞掉（真机 hook_active 缺失，卡片误显未激活）。
                    runCatching { HookStatus.markActive(getRemotePreferences(MODULE_PREFS_NAME)) }
                        .onFailure { Log.e("AutoOral", "markActive(remote) failed", it) }
                } catch (e: Throwable) {
                    Log.e("AutoOral", "hook after attach failed", e)
                }
                r
            }
        } catch (e: Throwable) {
            Log.e("AutoOral", "attach hook setup failed", e)
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