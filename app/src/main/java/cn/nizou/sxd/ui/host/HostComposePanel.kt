package cn.nizou.sxd.ui.host

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import cn.nizou.sxd.XposedInit
import cn.nizou.sxd.ui.settings.CustomScoreScreen
import cn.nizou.sxd.ui.settings.SettingsScreen
import cn.nizou.sxd.ui.theme.AutoOralTheme
import cn.nizou.sxd.util.StringRes

/**
 * 宿主注入面板（替代旧 SettingsDialog 的悬浮 AlertDialog）。
 *
 * 方案：在宿主 SettingsActivity 的 window 根视图（decorView）上**添加一个全屏 ComposeView
 * 覆盖层**，把模块的 Compose 设置页渲染进宿主界面。
 *
 * 为什么不是悬浮 Dialog：
 * - 旧实现用 `AlertDialog` 单独开一个 window，样式与宿主/ Material3 割裂、无法用 Compose 主题，
 *   且需手动 addAssetPath 才能解析模块资源。
 * - 本方案把 Compose 内容**注入宿主当前 window 的视图树**，作为一个全屏「页面面板」：
 *   共享宿主 window 的 insets/焦点/返回键语义，观感与宿主设置页融为一体；关闭即从视图树移除。
 * - 模块的 Compose 运行库已随模块 APK 打包并由模块 classloader 加载，宿主进程内可直接 new
 *   ComposeView + setContent（参考 WeKit 在微信内的注入做法）。
 *
 * 生命周期：注入的 ComposeView 使用自管理的 [XposedLifecycleOwner]，不依赖宿主 Activity 的
 * 生命周期类型；面板关闭时销毁 owner，避免泄漏。
 */
class HostComposePanel private constructor(
    private val activity: Activity,
    private val owner: XposedLifecycleOwner,
    private val composeView: ComposeView
) {
    /** 返回键处理器（宿主 Activity 的 onBackPressed 桥接至此）。 */
    var onBack: (() -> Unit)? = null

    fun show() {
        val decor = activity.window.decorView as ViewGroup
        composeView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        decor.addView(composeView)
    }

    fun dismiss() {
        val decor = activity.window.decorView as ViewGroup
        runCatching { decor.removeView(composeView) }
        runCatching { owner.onPause(); owner.onStop(); owner.onDestroy() }
    }

    companion object {
        /** 在宿主 Activity 的 decorView 上叠加一个全屏 Compose 设置面板。 */
        fun showSettings(activity: Activity): HostComposePanel {
            val owner = XposedLifecycleOwner.create()
            val backRef = mutableStateOf<(() -> Unit)?>(null)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    AutoOralTheme {
                        Surface(modifier = Modifier) {
                            SettingsScreen(
                                res = StringRes(XposedInit.moduleRes),
                                onBack = { backRef.value?.invoke() }
                            )
                        }
                    }
                }
            }
            val panel = HostComposePanel(activity, owner, composeView)
            // 面板自身的返回按钮 → 关闭自身；同时暴露给宿主 back 键桥接
            backRef.value = { panel.dismiss() }
            panel.onBack = { panel.dismiss() }
            panel.show()
            return panel
        }

        /** 在宿主 Activity 的 decorView 上叠加一个全屏 Compose 自定义分数面板。 */
        fun showCustomScore(activity: Activity): HostComposePanel {
            val owner = XposedLifecycleOwner.create()
            val backRef = mutableStateOf<(() -> Unit)?>(null)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    AutoOralTheme {
                        Surface(modifier = Modifier) {
                            CustomScoreScreen(onBack = { backRef.value?.invoke() })
                        }
                    }
                }
            }
            val panel = HostComposePanel(activity, owner, composeView)
            backRef.value = { panel.dismiss() }
            panel.onBack = { panel.dismiss() }
            panel.show()
            return panel
        }
    }
}
