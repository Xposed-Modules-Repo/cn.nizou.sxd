package cn.nizou.sxd.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.nizou.sxd.BuildConfig
import cn.nizou.sxd.XposedInit
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
import cn.nizou.sxd.ui.theme.AutoOralTheme
import cn.nizou.sxd.ui.theme.ThemeSettings
import cn.nizou.sxd.util.LogBuffer
import cn.nizou.sxd.util.StringRes
import cn.nizou.sxd.util.openGithub
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Article
import com.composables.icons.materialsymbols.outlined.Home
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Tune
import com.composables.icons.materialsymbols.outlinedfilled.Article
import com.composables.icons.materialsymbols.outlinedfilled.Home
import com.composables.icons.materialsymbols.outlinedfilled.Settings
import com.composables.icons.materialsymbols.outlinedfilled.Tune
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
 *  - 主界面 = `HorizontalPager`（4 tab：首页 / 功能 / 日志 / 设置）+ `FloatingBottomBar`
 *    悬浮胶囊底栏（LiquidGlass），tab 图标用 **MaterialSymbols 同款**（Home/Tune/Article/Settings）；
 *  - 首页 = 激活卡片（强制已激活）+ 设备信息区（api102 / 宿主版本 / 模块版本 / 构建时间 /
 *    设备型号 / 安卓版本 / 加载环境）+ GitHub；
 *  - 功能 = 分类菜单（通用/练习/PK/自定义分数/自定义答案/Debug/关于 下钻）；
 *  - 日志 = 模块运行日志（LogBuffer 实时）；
 *  - 设置 = 主题取色（HSV）+ 版本信息。
 *  - 悬浮底栏**不做内容底部留白限位**（用户要求，可遮挡内容）。
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
    AutoOralTheme(seedColor = ThemeSettings.seedColor) {
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
}

/** 悬浮底栏的分类 tab（照 WeKit TAB_ITEMS：MaterialSymbols 图标+文字）。 */
private data class NavItem(
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector,
)

private val MAIN_TABS = listOf(
    NavItem("首页", MaterialSymbols.Outlined.Home, MaterialSymbols.OutlinedFilled.Home),
    NavItem("功能", MaterialSymbols.Outlined.Tune, MaterialSymbols.OutlinedFilled.Tune),
    NavItem("日志", MaterialSymbols.Outlined.Article, MaterialSymbols.OutlinedFilled.Article),
    NavItem("设置", MaterialSymbols.Outlined.Settings, MaterialSymbols.OutlinedFilled.Settings),
)

/**
 * 主界面：HorizontalPager + FloatingBottomBar 悬浮胶囊底栏（对齐 WeKit MainPagerScreen）。
 * 悬浮底栏不占布局空间，内容可被其遮挡（用户要求，不做 CONTENT_BOTTOM_INSET 限位）。
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
            ) { p ->
                when (p) {
                    0 -> HomeTab(res, onNavigate = onNavigate, onBack = onBackToRoot)
                    1 -> FeaturesTab(res, onNavigate = onNavigate, onBack = onBackToRoot)
                    2 -> LogsTab(res, onBack = onBackToRoot)
                    else -> SettingsTab(res, onBack = onBackToRoot)
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
            iconContent = { item, index ->
                // 选中态用 filled 图标、非选中用 outlined（照 WeKit Crossfade 语义）
                Icon(
                    imageVector = if (index == pagerState.targetPage) item.filled else item.outlined,
                    contentDescription = item.label,
                )
            },
            labelContent = { item, _ ->
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            },
        )
    }
}

// ---------------------------------------------------------------------------
//  Page 0 — 首页（对齐 WeKit HomePager：激活卡片 + 设备信息 + GitHub）
// ---------------------------------------------------------------------------

private data class HomeInfoEntry(
    val title: String,
    val description: String,
)

/** 首页：激活卡片（强制已激活）+ 设备信息区 + GitHub。 */
@Composable
private fun HomeTab(
    res: StringRes,
    onNavigate: (MainRoute) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    M3ListScaffold(
        title = "老挂戏老叟",
        navigationIcon = { M3BackButton(onClick = onBack) },
    ) {
        item { HookStatusCard() }
        item {
            SegmentedColumn(title = "设备信息") {
                deviceInfoEntries().forEach { entry ->
                    BaseWidget(title = entry.title, description = entry.description)
                }
            }
        }
        item {
            SegmentedColumn(title = "了解更多") {
                BaseWidget(
                    title = "Github",
                    description = "github.com/sxd91/nizou",
                    onClick = { context.openGithub() },
                )
            }
        }
    }
}

/** 设备信息区（对齐 WeKit HomePager.DeviceInformation）。 */
@Composable
private fun deviceInfoEntries(): List<HomeInfoEntry> {
    val context = LocalContext.current
    val hostVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo("com.fenbi.android.leo", 0)
        }.getOrNull()
    }
    val frameworkInfo = remember {
        runCatching {
            val self = XposedInit.self
            "API ${self.apiVersion} · ${self.frameworkName}"
        }.getOrNull()
    }
    return listOf(
        HomeInfoEntry("加载环境", frameworkInfo ?: "未检测到框架（独立打开）"),
        HomeInfoEntry(
            "小猿口算版本",
            hostVersion?.let { "v${it.versionName} (${it.versionCode})" } ?: "未安装",
        ),
        HomeInfoEntry(
            "模块版本",
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        ),
        HomeInfoEntry(
            "构建时间",
            BuildConfig.BUILD_TIMESTAMP.takeIf { it > 0 }?.let { formatBuildTime(it) } ?: "—",
        ),
        HomeInfoEntry("设备型号", "${Build.MANUFACTURER} ${Build.MODEL}"),
        HomeInfoEntry("安卓版本", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
    )
}

private fun formatBuildTime(epochMillis: Long): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(epochMillis))
}

// ---------------------------------------------------------------------------
//  Page 1 — 功能（对齐 WeKit FeaturesPager：分类菜单下钻）
// ---------------------------------------------------------------------------

private data class FeatureMenuEntry(
    val title: String,
    val description: String?,
    val route: MainRoute,
)

@Composable
private fun FeaturesTab(
    res: StringRes,
    onNavigate: (MainRoute) -> Unit,
    onBack: () -> Unit,
) {
    val entries = remember {
        listOf(
            FeatureMenuEntry("通用", "识别/昵称通用开关", MainRoute.General),
            FeatureMenuEntry("练习", "口算练习自动答题", MainRoute.Practice),
            FeatureMenuEntry("PK", "极速/PK 自动答题", MainRoute.Pk),
            FeatureMenuEntry("自定义分数", "刷取指定分数", MainRoute.CustomScore),
            FeatureMenuEntry("自定义答案", "改题目/改答案/口算答案", MainRoute.CustomAnswer),
            FeatureMenuEntry("Debug", "调试开关", MainRoute.Debug),
            FeatureMenuEntry("关于", "版本与项目信息", MainRoute.About),
        )
    }

    M3ListScaffold(
        title = "功能",
        navigationIcon = { M3BackButton(onClick = onBack) },
    ) {
        item {
            SegmentedColumn(title = "功能菜单") {
                entries.forEach { entry ->
                    BaseWidget(
                        title = entry.title,
                        description = entry.description,
                        onClick = { onNavigate(entry.route) },
                        trailingContent = {
                            Text("›", style = MaterialTheme.typography.titleLarge)
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Page 2 — 日志（LogBuffer 实时日志）
// ---------------------------------------------------------------------------

@Composable
private fun LogsTab(
    res: StringRes,
    onBack: () -> Unit,
) {
    var refresh by remember { mutableStateOf(0) }
    val logs = remember(refresh) { LogBuffer.snapshot() }

    M3ListScaffold(
        title = "日志",
        navigationIcon = { M3BackButton(onClick = onBack) },
    ) {
        item {
            BaseWidget(
                title = "刷新日志",
                description = "共 ${logs.size} 条（环形缓冲容量 ${LogBuffer.CAPACITY}）",
                onClick = { refresh++ },
                trailingContent = { Text("↻", style = MaterialTheme.typography.titleLarge) },
            )
        }
        item {
            SegmentedColumn(title = "运行日志") {
                if (logs.isEmpty()) {
                    BaseWidget(title = "暂无日志", description = "模块运行后自动写入")
                } else {
                    logs.forEach { line ->
                        BaseWidget(title = line, description = null)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Page 3 — 设置（主题取色 + 版本信息）
// ---------------------------------------------------------------------------

@Composable
private fun SettingsTab(
    res: StringRes,
    onBack: () -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val seedColor = ThemeSettings.seedColor
    val seedColorHex = remember(seedColor) { ThemeSettings.seedColorHex() }
    val context = LocalContext.current

    M3ListScaffold(
        title = "设置",
        navigationIcon = { M3BackButton(onClick = onBack) },
    ) {
        item {
            SegmentedColumn(title = "界面") {
                BaseWidget(
                    title = "自定义模块取色",
                    description = "当前：$seedColorHex",
                    onClick = { showColorPicker = true },
                    trailingContent = {
                        Box(
                            Modifier
                                .padding(vertical = 4.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(seedColor)),
                        )
                    },
                )
            }
        }
        item {
            SegmentedColumn(title = "关于") {
                BaseWidget(
                    title = "版本",
                    description = "v${BuildConfig.VERSION_NAME}",
                )
                BaseWidget(
                    title = "Github",
                    description = "github.com/sxd91/nizou",
                    onClick = { context.openGithub() },
                )
            }
        }
    }

    if (showColorPicker) {
        SeedColorPickerDialog(
            initialColor = seedColor,
            onConfirm = { ThemeSettings.updateSeedColor(it) },
            onDismiss = { showColorPicker = false },
        )
    }
}

/** HSV 取色对话框（照抄 WeKit SeedColorPickerDialog：Hue/Saturation/Value 三滑杆 + 预览）。 */
@Composable
private fun SeedColorPickerDialog(
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember { ThemeSettings.colorToHsv(initialColor) }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1] * 100f) }
    var value by remember { mutableStateOf(initialHsv[2] * 100f) }
    val picked = ThemeSettings.hsvToColor(hue, saturation / 100f, value / 100f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义模块取色") },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(picked)),
                )
                Spacer(Modifier.height(16.dp))
                HsvSlider("色相", hue, 0f..360f, { hue = it })
                HsvSlider("饱和度", saturation, 0f..100f, { saturation = it })
                HsvSlider("明度", value, 0f..100f, { value = it })
                TextButton(onClick = {
                    val reset = ThemeSettings.colorToHsv(ThemeSettings.DEFAULT_SEED_COLOR)
                    hue = reset[0]
                    saturation = reset[1] * 100f
                    value = reset[2] * 100f
                }) {
                    Text("重置")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ThemeSettings.hsvToColor(hue, saturation / 100f, value / 100f))
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun HsvSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(
            text = "$label: ${value.toInt()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}
