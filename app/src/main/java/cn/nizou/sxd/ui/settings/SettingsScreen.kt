package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.ui.components.BaseWidget
import cn.nizou.sxd.ui.components.HookStatusCard
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.ui.components.M3ListScaffold
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.util.StringRes

/**
 * 导航目标（照抄 WeKit `SettingsRoute` 模型的简化版，去掉 pager/第三方 nav 依赖）。
 * 「菜单与内容分离」：Main 是功能菜单列表页，点进某一项进入对应 Category/功能详情页。
 */
sealed interface SettingsRoute {
    data object Main : SettingsRoute
    data object General : SettingsRoute
    data object Practice : SettingsRoute
    data object Pk : SettingsRoute
    data object CustomScore : SettingsRoute
    data object CustomAnswer : SettingsRoute
    data object Debug : SettingsRoute
    data object About : SettingsRoute
}

/**
 * 设置页根。独立 MainActivity 与宿主注入面板共用：Main 菜单列表 + 分类详情页导航。
 *
 * @param res StringRes 实例（独立 MainActivity 传模块自身资源；宿主面板传模块 APK 加载的 resources）。
 * @param onBack 关闭整个面板/页面（回退到 Main 之后再按返回才触发）。
 */
@Composable
fun SettingsScreen(
    res: StringRes,
    onBack: () -> Unit
) {
    // 极简内存 backstack：Main 为根，navigate 压栈，goBack 弹栈；栈根再按返回则 onBack。
    val backStack = remember { mutableStateListOf<SettingsRoute>(SettingsRoute.Main) }
    val current = backStack.last()

    fun navigate(route: SettingsRoute) {
        backStack.add(route)
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else {
            onBack()
        }
    }

    when (current) {
        SettingsRoute.Main -> SettingsMainScreen(onNavigate = ::navigate, onBack = ::goBack)
        SettingsRoute.General -> GeneralScreen(res, onBack = ::goBack)
        SettingsRoute.Practice -> PracticeScreen(res, onBack = ::goBack)
        SettingsRoute.Pk -> PkScreen(res, onBack = ::goBack)
        SettingsRoute.CustomScore -> CustomScoreScreen(onBack = ::goBack)
        SettingsRoute.CustomAnswer -> CustomAnswerScreen(res, onBack = ::goBack)
        SettingsRoute.Debug -> DebugScreen(res, onBack = ::goBack)
        SettingsRoute.About -> AboutScreen(onBack = ::goBack)
    }
}

private data class MenuEntry(
    val title: String,
    val description: String?,
    val route: SettingsRoute
)

/**
 * 功能菜单列表。增删功能 = 加一个 [MenuEntry] + 一个路由 + 一个详情 Composable，即插即用。
 */
private fun moduleMenuEntries(): List<MenuEntry> = listOf(
    MenuEntry("通用", "识别/昵称通用开关", SettingsRoute.General),
    MenuEntry("练习", "口算练习自动答题", SettingsRoute.Practice),
    MenuEntry("PK", "极速/PK 自动答题", SettingsRoute.Pk),
    MenuEntry("自定义分数", "刷取指定分数", SettingsRoute.CustomScore),
    MenuEntry("自定义答案", "改题目/改答案/口算答案", SettingsRoute.CustomAnswer),
    MenuEntry("Debug", "调试开关", SettingsRoute.Debug),
    MenuEntry("关于", "版本与项目信息", SettingsRoute.About)
)

@Composable
internal fun SettingsMainScreen(
    onNavigate: (SettingsRoute) -> Unit,
    onBack: () -> Unit
) {
    M3ListScaffold(
        title = "老挂戏老叟",
        navigationIcon = { M3BackButton(onClick = onBack) }
    ) {
        item { HookStatusCard() }
        item {
            SegmentedColumn(title = "功能菜单") {
                moduleMenuEntries().forEach { entry ->
                    BaseWidget(
                        title = entry.title,
                        description = entry.description,
                        onClick = { onNavigate(entry.route) },
                        trailingContent = {
                            Text("›", style = MaterialTheme.typography.titleLarge)
                        }
                    )
                }
            }
        }
        item { Box(Modifier.padding(24.dp)) {} }
    }
}
