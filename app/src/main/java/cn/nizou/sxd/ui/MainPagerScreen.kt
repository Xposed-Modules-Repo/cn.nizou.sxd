package cn.nizou.sxd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.nizou.sxd.ui.components.BaseWidget
import cn.nizou.sxd.ui.components.HookStatusCard
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.ui.components.M3ListScaffold
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.ui.content.FloatingBottomBar
import cn.nizou.sxd.ui.content.FloatingBottomBarDefaults
import cn.nizou.sxd.ui.settings.AboutScreen
import cn.nizou.sxd.ui.settings.CustomAnswerScreen
import cn.nizou.sxd.ui.settings.CustomScoreScreen
import cn.nizou.sxd.ui.settings.DebugScreen
import cn.nizou.sxd.ui.settings.GeneralScreen
import cn.nizou.sxd.ui.settings.PkScreen
import cn.nizou.sxd.ui.settings.PracticeScreen
import cn.nizou.sxd.util.StringRes
import cn.nizou.sxd.util.openGithub
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 共用导航目标。模块本体（独立 App）与注入宿主面板共用同一批页面 Composable 与同一套
 * `MainPagerScreen` 结构，与 WeKit「本体 / 注入共用组件」一致。
 */
sealed interface MainRoute {
    data object Main : MainRoute
    data object General : MainRoute
    data object Practice : MainRoute
    data object Pk : MainRoute
    data object CustomScore : MainRoute
    data object CustomAnswer : MainRoute
    data object Debug : MainRoute
    data object About : MainRoute
}

/**
 * 共用根容器。架构对齐 WeKit `SettingsActivity.MainPagerScreen`：
 *  - 主界面 = `HorizontalPager`（4 tab：首页 / 通用 / 练习 / PK）+ `FloatingBottomBar` 悬浮胶囊底栏；
 *  - 首页 tab 内是「功能菜单列表」（激活检测卡片 + GitHub + 不在底栏的分类下钻入口）；
 *  - 其余分类直接作为 pager 页复用 [GeneralScreen]/[PracticeScreen]/[PkScreen]；
 *  - 不在底栏的分类（自定义分数/自定义答案/Debug/关于）从首页菜单下钻（压栈全屏详情页）。
 *
 * 模块本体与注入面板共用本容器：`onFinish` 在模块本体为关闭 Activity、在注入面板为关闭
 * 底部弹出的 ComponentDialog。
 *
 * @param res StringRes 实例。
 * @param onFinish 退到栈根再返回时触发（关闭整个界面）。
 */
@Composable
fun MainPagerScreen(
    res: StringRes,
    onFinish: () -> Unit,
) {
    val backStack = remember { mutableStateListOf<MainRoute>(MainRoute.Main) }
    val current = backStack.last()

    fun navigate(route: MainRoute) {
        backStack.add(route)
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else {
            onFinish()
        }
    }

    when (current) {
        MainRoute.Main -> MainPager(
            res = res,
            onNavigate = ::navigate,
            onBackToRoot = ::goBack,
        )
        MainRoute.General -> GeneralScreen(res, onBack = ::goBack)
        MainRoute.Practice -> PracticeScreen(res, onBack = ::goBack)
        MainRoute.Pk -> PkScreen(res, onBack = ::goBack)
        MainRoute.CustomScore -> CustomScoreScreen(onBack = ::goBack)
        MainRoute.CustomAnswer -> CustomAnswerScreen(res, onBack = ::goBack)
        MainRoute.Debug -> DebugScreen(res, onBack = ::goBack)
        MainRoute.About -> AboutScreen(onBack = ::goBack)
    }
}

/** 悬浮底栏的分类 tab（照 WeKit TAB_ITEMS 用图标+文字；项目不引图标库，沿用文本字符/emoji）。 */
private enum class MainTab(val label: String, val icon: String) {
    Home("首页", "🏠"),
    General("通用", "⚙"),
    Practice("练习", "✍"),
    Pk("PK", "⚡"),
}

private val MAIN_TABS = MainTab.entries

/** 内容区底部留白：让滚动的设置内容清出悬浮底栏（照 WeKit CONTENT_BOTTOM_INSET=88dp）。 */
private val CONTENT_BOTTOM_INSET = 88.dp

/**
 * 主界面：pager 首 tab 为「功能菜单列表」首页，其余 tab 直接复用对应分类页；
 * [FloatingBottomBar] 悬浮胶囊底栏切换分类（对齐 WeKit MainPagerScreen）。
 */
@Composable
private fun MainPager(
    res: StringRes,
    onNavigate: (MainRoute) -> Unit,
    onBackToRoot: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val barBottomPadding = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pagerState = rememberPagerState(pageCount = { MAIN_TABS.size })

    Box(Modifier.fillMaxSize()) {
        // 内容层捕获 backdrop，供底部悬浮底栏做 LiquidGlass 反射/模糊。
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> HomeTab(res, onNavigate = onNavigate, onBack = onBackToRoot)
                    1 -> Box(Modifier.padding(bottom = CONTENT_BOTTOM_INSET)) {
                        GeneralScreen(res, onBack = { onBackToRoot() })
                    }
                    2 -> Box(Modifier.padding(bottom = CONTENT_BOTTOM_INSET)) {
                        PracticeScreen(res, onBack = { onBackToRoot() })
                    }
                    else -> Box(Modifier.padding(bottom = CONTENT_BOTTOM_INSET)) {
                        PkScreen(res, onBack = { onBackToRoot() })
                    }
                }
            }
        }

        FloatingBottomBar(
            items = MAIN_TABS,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = barBottomPadding),
            selectedIndex = { pagerState.targetPage },
            onSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
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

private data class HomeMenuEntry(
    val title: String,
    val description: String?,
    val route: MainRoute,
)

/** 首页「功能菜单列表」：激活检测卡片 + GitHub + 未放进底栏的分类下钻入口。 */
@Composable
private fun HomeTab(
    res: StringRes,
    onNavigate: (MainRoute) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // 底栏已直接覆盖 通用/练习/PK；首页菜单放其余分类 + GitHub，避免重复入口。
    val menuEntries = remember {
        listOf(
            HomeMenuEntry("自定义分数", "刷取指定分数", MainRoute.CustomScore),
            HomeMenuEntry("自定义答案", "改题目/改答案/口算答案", MainRoute.CustomAnswer),
            HomeMenuEntry("Debug", "调试开关", MainRoute.Debug),
            HomeMenuEntry("关于", "版本与项目信息", MainRoute.About),
        )
    }

    M3ListScaffold(
        title = "老挂戏老叟",
        navigationIcon = { M3BackButton(onClick = onBack) },
    ) {
        item { HookStatusCard() }
        item {
            SegmentedColumn(title = "功能菜单") {
                menuEntries.forEach { entry ->
                    BaseWidget(
                        title = entry.title,
                        description = entry.description,
                        onClick = { onNavigate(entry.route) },
                        trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
                    )
                }
                BaseWidget(
                    title = "Github",
                    description = "github.com/sxd91/nizou",
                    onClick = { context.openGithub() },
                    trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
                )
            }
        }
        item { Box(Modifier.padding(bottom = CONTENT_BOTTOM_INSET)) {} }
    }
}
