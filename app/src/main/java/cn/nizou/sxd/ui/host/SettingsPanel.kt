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
import cn.nizou.sxd.util.StringRes
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 模块全部注入面板的导航目标（rail 侧菜单用）。
 *
 * 用文本字符/emoji 充当图标——项目刻意不引入图标库，与 M3BackButton 的文本 chevron 约定一致。
 */
enum class PanelDestination(val label: String, val icon: String) {
    General("通用", "⚙"),
    Practice("练习", "✍"),
    Pk("PK", "⚡"),
    CustomScore("分数", "🎯"),
    CustomAnswer("答案", "🅰"),
    Debug("调试", "🐞"),
    About("关于", "ℹ"),
}

/** 悬浮底栏的快捷 tab（受 rail 宽度限制不能全放，挑主入口；二者都驱动同一个 [selected]）。 */
private val bottomBarDestinations = listOf(
    PanelDestination.General,
    PanelDestination.Practice,
    PanelDestination.Pk,
    PanelDestination.Debug,
    PanelDestination.About,
)

/** 内容区底部留白：让滚动的设置内容能清出悬浮底栏（照抄 WeKit CONTENT_BOTTOM_INSET=88dp）。 */
private val CONTENT_BOTTOM_INSET = 88.dp

/**
 * 注入面板主体（对齐 WeKit 架构）：
 *  - 底部弹出（由 [HostComposePanel] 用 showPanelDialog 式 ComponentDialog 承载）；
 *  - [PanelShell] rail 侧菜单（7 个设置分类常驻，点选直接切换内容区）；
 *  - 底部 [FloatingBottomBar] 悬浮胶囊底栏（LiquidGlass），与 rail 联动切换。
 *
 * 渲染某个分类时复用既有 [*Screen]（各页自带 M3ListScaffold + 返回按钮，
 * 返回即 [onDismiss] 关掉整个注入面板）。
 */
@Composable
fun SettingsPanel(
    res: StringRes,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(PanelDestination.General) }
    val backdrop = rememberLayerBackdrop()

    val railItems = remember {
        PanelDestination.entries.map { destination ->
            PanelRailItem(
                destination = destination,
                label = destination.label,
                icon = { Text(destination.icon, fontSize = 18.sp) },
            )
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
                selected = selected,
                title = selected.label,
                onSelect = { selected = it },
                onDismiss = onDismiss,
                onBack = onDismiss,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = CONTENT_BOTTOM_INSET),
                ) {
                    when (selected) {
                        PanelDestination.General -> GeneralScreen(res, onBack = onDismiss)
                        PanelDestination.Practice -> PracticeScreen(res, onBack = onDismiss)
                        PanelDestination.Pk -> PkScreen(res, onBack = onDismiss)
                        PanelDestination.CustomScore -> CustomScoreScreen(onBack = onDismiss)
                        PanelDestination.CustomAnswer -> CustomAnswerScreen(res, onBack = onDismiss)
                        PanelDestination.Debug -> DebugScreen(res, onBack = onDismiss)
                        PanelDestination.About -> AboutScreen(onBack = onDismiss)
                    }
                }
            }
        }

        // 悬浮胶囊底栏（LiquidGlass）——注入面板的主导航。
        FloatingBottomBar(
            items = bottomBarDestinations,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            selectedIndex = { bottomBarDestinations.indexOf(selected).coerceAtLeast(0) },
            onSelected = { selected = bottomBarDestinations[it] },
            backdrop = backdrop,
            colors = FloatingBottomBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                indicatorColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeContentColor = MaterialTheme.colorScheme.primary,
            ),
            iconContent = { item, _ ->
                Text(item.icon, fontSize = 22.sp)
            },
            labelContent = { item, _ ->
                Text(
                    text = item.label,
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
