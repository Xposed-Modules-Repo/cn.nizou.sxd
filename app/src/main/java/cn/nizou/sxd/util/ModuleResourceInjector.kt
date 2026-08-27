package cn.nizou.sxd.util

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import cn.nizou.sxd.R
import cn.nizou.sxd.XposedInit
import java.io.File
import java.io.IOException

/**
 * 模块资源注入（借鉴 WeKit ResourcesInjector）。
 *
 * 宿主进程内，Compose 组件会通过注入面板的 Context 解析自己的资源（R.string / R.style /
 * R.dimen 等，打包在模块 APK、resopt 保留包 ID 0x23）。若该 Context 的 Resources 不含模块
 * APK，Compose/Material3 组件查资源会 NotFoundException → 闪退。
 *
 * 这里把模块 APK（XposedInit.modulePath）注入给定 Resources 的 AssetManager / ResourceLoader，
 * 使模块资源在注入 Context 内可解析；同一 Resources 只注入一次。
 */
object ModuleResourceInjector {

    private const val TAG = "ModuleResInjector"

    @SuppressLint("DiscouragedApi", "PrivateApi")
    @Suppress("DEPRECATION")
    private fun hasModuleRes(resources: Resources): Boolean = try {
        resources.getValue(R.string.app_name, android.util.TypedValue(), true)
        true
    } catch (_: Resources.NotFoundException) {
        false
    }

    fun injectModuleRes(resources: Resources?) {
        resources ?: return
        if (hasModuleRes(resources)) return

        val modulePath = XposedInit.modulePath
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (injectGte30(resources, modulePath)) return
        }
        injectLt30(resources, modulePath)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun injectGte30(resources: Resources, path: String): Boolean {
        return try {
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                val provider = ResourcesProvider.loadFromApk(descriptor)
                val loader = ResourcesLoader().apply { addProvider(provider) }
                resources.addLoaders(loader)
            }
            true
        } catch (_: IOException) {
            logFailure(path, "ResourcesProvider.loadFromApk")
            false
        } catch (e: IllegalArgumentException) {
            // 未注册的 ResourcesImpl 不能 addLoaders，退回 addAssetPath
            logFailure(path, "addLoaders(${e.message})")
            false
        }
    }

    @SuppressLint("DiscouragedApi", "PrivateApi")
    @Suppress("JavaReflectionMemberAccess")
    private fun injectLt30(resources: Resources, path: String) {
        try {
            val am = resources.assets
            val addAssetPath = AssetManager::class.java
                .getDeclaredMethod("addAssetPath", String::class.java)
                .apply { isAccessible = true }
            addAssetPath.invoke(am, path)
            if (!hasModuleRes(resources)) {
                logFailure(path, "addAssetPath 后仍未解析到模块资源")
            }
        } catch (e: Exception) {
            logFailure(path, "addAssetPath(${e.message})")
        }
    }

    private fun logFailure(path: String, step: String) {
        logI("$TAG 注入失败 step=$step path=$path exists=${File(path).exists()}")
    }
}
