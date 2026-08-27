package cn.nizou.sxd.ui.host

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import cn.nizou.sxd.XposedInit
import cn.nizou.sxd.ui.theme.AutoOralTheme
import cn.nizou.sxd.util.StringRes

/**
 * 宿主注入面板（对齐 WeKit `showPanelDialog`：底部弹出 + 点外关闭）。
 *
 * 崩溃根因修复（问题 1「注入菜单打不开」）：旧实现把 `ComposeView` 直接 addView 到宿主
 * SettingsActivity 的 decorView 上，Compose 在 onAttachedToWindow 时从 view 树向上找
 * `ViewTreeLifecycleOwner`，而宿主 DecorView 上没有 —— 抛
 * `IllegalStateException: ViewTreeLifecycleOwner not found from DecorView[SettingsActivity]`，
 * 宿主崩溃（真机已复现）。
 *
 * 修复：改用 **ComponentDialog**（androidx.activity）开一个**独立 window** 承载 Compose。
 * ComponentDialog 自身即 LifecycleOwner，ComposeView 在独立 window 里能正常解析 composition
 * context，不再依赖宿主 DecorView 的 lifecycle。这是 wekit 注入微信 Compose 面板的成熟做法。
 *
 * 本版再把 window 对齐 WeKit `showPanelDialog`：
 *  - gravity = BOTTOM（从底部弹出）；
 *  - 透明背景 + FLAG_DIM_BEHIND(0.3) 压暗宿主；
 *  - 内容区占窗口下 65% 高度，外层 Box 点空白即关闭（内容区自身拦截点击）。
 */
class HostComposePanel private constructor(
    private val dialog: ComponentDialog
) {
    fun show() = dialog.show()

    fun dismiss() {
        runCatching { dialog.dismiss() }
    }

    companion object {
        /** 在宿主 SettingsActivity 底部弹出 Compose 设置面板（独立 window）。 */
        fun showSettings(activity: Activity): HostComposePanel =
            showPanel(activity) { onDismiss ->
                SettingsPanel(
                    res = StringRes(XposedInit.moduleRes),
                    onDismiss = onDismiss
                )
            }

        private fun showPanel(
            activity: Activity,
            content: @Composable (onDismiss: () -> Unit) -> Unit
        ): HostComposePanel {
            // 模块 classLoader context：让宿主进程内能加载模块打包的 Compose 运行库与模块资源。
            val moduleContext = ModuleContextWrapper.wrap(activity)
            val isDark =
                (moduleContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            val dialog = ComponentDialog(
                moduleContext,
                android.R.style.Theme_Translucent_NoTitleBar
            )
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.window?.apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                clearFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                )
                WindowCompat.setDecorFitsSystemWindows(this, false)
                statusBarColor = Color.TRANSPARENT
                navigationBarColor = Color.TRANSPARENT
                navigationBarDividerColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    isStatusBarContrastEnforced = false
                    isNavigationBarContrastEnforced = false
                }
                WindowInsetsControllerCompat(this, decorView).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.3f)
                setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN,
                )
                attributes = attributes.apply { gravity = Gravity.BOTTOM }
            }
            dialog.setCancelable(true)

            // ComponentDialog 自身即 LifecycleOwner + SavedStateRegistryOwner，ComposeView 用它即可正常重组。
            val composeView = ComposeView(moduleContext).apply {
                setViewTreeLifecycleOwner(dialog)
                setViewTreeSavedStateRegistryOwner(dialog)
                setContent {
                    AutoOralTheme {
                        // showPanelDialog 式：全屏点外关闭；内容区占下 65% 高，内部拦截点击避免误关。
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding()
                                .clickable(
                                    indication = null,
                                    interactionSource = null,
                                    onClick = { dialog.dismiss() },
                                ),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.65f)
                                    .clickable(
                                        indication = null,
                                        interactionSource = null,
                                        onClick = {},
                                    ),
                            ) {
                                content { dialog.dismiss() }
                            }
                        }
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

            val panel = HostComposePanel(dialog)
            panel.show() // 立即显示，否则点了没反应
            dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            return panel
        }
    }
}
