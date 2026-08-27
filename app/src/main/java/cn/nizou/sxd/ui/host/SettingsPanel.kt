package cn.nizou.sxd.ui.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.nizou.sxd.ui.content.FloatingBottomBar
import cn.nizou.sxd.ui.content.FloatingBottomBarDefaults
import cn.nizou.sxd.ui.panel.PanelRailItem
import cn.nizou.sxd.ui.panel.PanelShell
import cn.nizou.sxd.ui.settings.AboutScreen
import cn.nizou.sxd.ui.settings.CustomAnswerScreen
import cn.nizou.sxd.ui.settings.CustomScoreScreen
import cn.nizou.sxd.ui.settings.DebugScreen
import cn.nizou.sxd.ui.settings.GeneralScreen
import cn.nizou.sxd.ui.settings.PkScreen
import cn.nizou.sxd.ui.settings.PracticeScreen
import cn.nizou.sxd.ui.settings.SettingsMainScreen
import cn.nizou.sxd.ui.settings.SettingsRoute
import cn.nizou.sxd.util.StringRes
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** rail 项标题（图标/文案用文本字符——项目刻意不引入图标库）。 */
private fun SettingsRoute.railLabel(): String = when (this) {
    SettingsRoute.Main -> "主页"
    SettingsRoute.General -> "通用"
    SettingsRoute.Practice -> "练习"
    SettingsRoute.Pk -> "PK"
    SettingsRoute.CustomScore -> "分数"
    SettingsRoute.CustomAnswer -> "答案"
    SettingsRoute.Debug -> "调试"
    SettingsRoute.About -> "关于"
}

private fun SettingsRoute.railIcon(): String = when (this) {
    SettingsRoute.Main -> "🏠"
    SettingsRoute.General -> "⚙"
    SettingsRoute.Practice -> "✍"
    SettingsRoute.Pk -> "⚡"
    SettingsRoute.CustomScore -> "🎯"
    SettingsRoute.CustomAnswer -> "🅰"
    SettingsRoute.Debug -> "🐞"
    SettingsRoute.About -> "ℹ"
}

/** 悬浮底栏快捷 tab（FloatingBottomBar 每 tab min 76dp，屏宽放不下 8 项，挑 4 个主入口）。 */
private val bottomBarDestinations = listOf(
    SettingsRoute.General,
    SettingsRoute.Practice,
    SettingsRoute.Pk,
    SettingsRoute.About,
)

/** 内容区底部留白：让滚动的设置内容清出悬浮底栏（照抄 WeKit CONTENT_BOTTOM_INSET=88dp）。 */
private val CONTENT_BOTTOM_INSET = 88.dp

/**
 * 注入面板主体（对齐 WeKit 架构）：
 *  - 底部弹出（由 [HostComposePanel] 用 showPanelDialog 式 ComponentDialog 承载）；
 *  - [PanelShell] rail 侧菜单：现有 [SettingsRoute]（Main/General/Practice/Pk/CustomScore/
 *    CustomAnswer/Debug/About）做成左侧图标 rail，点选直接切换右侧内容区；
 *  - 底部 [FloatingBottomBar] 悬浮胶囊底栏（LiquidGlass），与 rail 联动切换。
 *
 * 功能保持：Main 的激活卡片（HookStatusCard）与功能菜单、各分类设置项、Simian 自定义答案
 *  UI（CustomAnswerScreen）全部保留。
 */
@Composable
fun SettingsPanel(
    res: StringRes,
    onDismiss: () -> Unit,
) {
    var route by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Main) }
    val backdrop = rememberLayerBackdrop()

    val allRoutes = remember {
        listOf(
            SettingsRoute.Main,
            SettingsRoute.General,
            SettingsRoute.Practice,
            SettingsRoute.Pk,
            SettingsRoute.CustomScore,
            SettingsRoute.CustomAnswer,
            SettingsRoute.Debug,
            SettingsRoute.About,
        )
    }
    val railItems = remember(allRoutes) {
        allRoutes.map { destination ->
            PanelRailItem(
                destination = destination,
                label = destination.railLabel(),
                icon = { Text(destination.railIcon(), fontSize = 18.sp) },
            )
        }
    }

    // 分类返回先回 Main，Main 再返回/点关闭才关整个面板。
    fun goHomeOrDismiss() {
        if (route != SettingsRoute.Main) {
            route = SettingsRoute.Main
        } else {
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 内容层捕获 backdrop，供底部悬浮底栏做 LiquidGlass 反射/模糊。
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .layerBackdrop(backdrop),
        ) {
            PanelShell(
                railItems = railItems,
                selected = route,
                title = route.railLabel(),
                onSelect = { route = it },
                onDismiss = onDismiss,
                onBack = { goHomeOrDismiss() },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = CONTENT_BOTTOM_INSET),
                ) {
                    when (route) {
                        SettingsRoute.Main -> SettingsMainScreen(
                            onNavigate = { route = it },
                            onBack = onDismiss,
                        )
                        SettingsRoute.General -> GeneralScreen(res, onBack = { goHomeOrDismiss() })
                        SettingsRoute.Practice -> PracticeScreen(res, onBack = { goHomeOrDismiss() })
                        SettingsRoute.Pk -> PkScreen(res, onBack = { goHomeOrDismiss() })
                        SettingsRoute.CustomScore -> CustomScoreScreen(onBack = { goHomeOrDismiss() })
                        SettingsRoute.CustomAnswer -> CustomAnswerScreen(res, onBack = { goHomeOrDismiss() })
                        SettingsRoute.Debug -> DebugScreen(res, onBack = { goHomeOrDismiss() })
                        SettingsRoute.About -> AboutScreen(onBack = { goHomeOrDismiss() })
                    }
                }
            }
        }

        // 悬浮胶囊底栏（LiquidGlass）——注入面板内的底部 tab 导航。
        FloatingBottomBar(
            items = bottomBarDestinations,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            selectedIndex = { bottomBarDestinations.indexOf(route).coerceAtLeast(0) },
            onSelected = { route = bottomBarDestinations[it] },
            backdrop = backdrop,
            colors = FloatingBottomBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                indicatorColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeContentColor = MaterialTheme.colorScheme.primary,
            ),
            iconContent = { item, _ ->
                Text(item.railIcon(), fontSize = 22.sp)
            },
            labelContent = { item, _ ->
                Text(
                    text = item.railLabel(),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            },
        )
    }
}
