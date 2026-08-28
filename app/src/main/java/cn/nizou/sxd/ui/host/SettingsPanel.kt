package cn.nizou.sxd.ui.host

import androidx.compose.runtime.Composable
import cn.nizou.sxd.ui.MainPagerScreen
import cn.nizou.sxd.util.StringRes

/**
 * 注入面板主体。
 *
 * 与 WeKit 架构一致：注入设置菜单即 `MainPagerScreen`（`HorizontalPager` + `FloatingBottomBar`
 * 悬浮胶囊底栏 + `M3ListScaffold` + `SegmentedColumn`），与模块本体共用同一套结构与页面。
 * 本组件只是 `MainPagerScreen` 的一个薄包装，把「关闭面板」接到 [onDismiss]。
 *
 * 历史说明：旧实现错误地把 `PanelShell`（WeKit 语音/表情面板的 rail 侧菜单）当作注入设置
 * 菜单；WeKit 真正的注入设置菜单是 `SettingsActivity` 的 `MainPagerScreen`。现已对齐。
 */
@Composable
fun SettingsPanel(
    res: StringRes,
    onDismiss: () -> Unit,
) {
    MainPagerScreen(
        res = res,
        onFinish = onDismiss,
    )
}
