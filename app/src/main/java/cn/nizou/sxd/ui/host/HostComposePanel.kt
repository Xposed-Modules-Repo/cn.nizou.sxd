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
 * 宿主注入面板。
 *
 * 崩溃根因修复：宿主 Activity 的 context 的 classLoader 是**宿主** classLoader，没有 Compose 类，
 * 直接 `new ComposeView(activity)` 会 NoClassDefFound → 闪退。这里用 [ModuleContextWrapper]
 * 包装 context：getClassLoader() 返回**模块** classLoader（含模块打包的 Compose 运行库），
 * 并把模块资源注入其 Resources；ComposeView 用该包装 context 创建。生命周期经
 * [LifecycleProvider] 转发宿主 Activity 事件。
 *
 * 方案：在宿主 SettingsActivity 的 decorView 上叠加一个全屏 ComposeView 覆盖层，渲染模块的
 * Compose 设置页。关闭即从视图树移除并销毁 owner。
 */
class HostComposePanel private constructor(
    private val activity: Activity,
    private val owner: XposedLifecycleOwner,
    private val composeView: ComposeView
) {
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
        // owner 生命周期交给 LifecycleProvider 转发；这里仅触发销毁（若宿主已销毁则幂等）。
        runCatching { owner.onDestroy() }
    }

    companion object {
        /** 在宿主 Activity 的 decorView 上叠加一个全屏 Compose 设置面板。 */
        fun showSettings(activity: Activity): HostComposePanel =
            showPanel(activity) { onBack ->
                SettingsScreen(
                    res = StringRes(XposedInit.moduleRes),
                    onBack = onBack
                )
            }

        /** 在宿主 Activity 的 decorView 上叠加一个全屏 Compose 自定义分数面板。 */
        fun showCustomScore(activity: Activity): HostComposePanel =
            showPanel(activity) { onBack ->
                CustomScoreScreen(onBack = onBack)
            }

        private fun showPanel(
            activity: Activity,
            content: @Composable (onBack: () -> Unit) -> Unit
        ): HostComposePanel {
            val owner = XposedLifecycleOwner.forActivity(activity)
            val backRef = mutableStateOf<(() -> Unit)?>(null)
            // 关键修复：用模块 classLoader context 创建 ComposeView，避免宿主无 Compose 类闪退。
            val moduleContext = ModuleContextWrapper.wrap(activity)
            val composeView = ComposeView(moduleContext).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    AutoOralTheme {
                        Surface(modifier = Modifier) {
                            content { backRef.value?.invoke() }
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
