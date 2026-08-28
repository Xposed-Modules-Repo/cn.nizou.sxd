@file:OptIn(ExperimentalMaterial3Api::class)

package cn.nizou.sxd.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.nizou.sxd.ui.components.BaseItemContainer
import cn.nizou.sxd.ui.components.DropDownMenuWidget
import cn.nizou.sxd.ui.components.DropdownOption
import cn.nizou.sxd.XposedInit
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.util.StringRes
import cn.nizou.sxd.util.WeLogger
import cn.nizou.sxd.util.crash.CrashLogsManager
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Delete_sweep
import com.composables.icons.materialsymbols.outlined.Expand_more
import com.composables.icons.materialsymbols.outlined.Keyboard_double_arrow_down
import com.composables.icons.materialsymbols.outlined.Keyboard_double_arrow_up
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Refresh
import com.composables.icons.materialsymbols.outlined.Save
import com.composables.icons.materialsymbols.outlined.Share
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.math.log10
import kotlin.math.pow

private const val LOGS_TAG = "LogsScreen"

/**
 * 日志查看页（移植自 WeKit `activity/settings/LogsPager.kt`，替换原 MainPagerScreen 的
 * LogsTab 内存快照页）。
 *
 * 与 wekit 的差异：
 *  - 文案全部硬编码中文（项目无 i18n 体系）；
 *  - 无 FileProvider：分享用 `ACTION_SEND` text/plain 内联文本（超大日志截断，防
 *    Binder 1MB 限制）；
 *  - 无 wekit 的 TransparentActivity：保存用 [ActivityResultContracts.CreateDocument]，
 *    仅当能解析到 [ComponentActivity]（独立模块 MainActivity，或宿主恰好是
 *    ComponentActivity）时可用，否则隐藏保存按钮（宿主面板环境不支持）；
 *  - 顶部栏不用项目 M3ListScaffold（无 actions 槽），本页自建 TopAppBar + TabRow；
 *  - 解析（parseRunLog / parseCrashLog）、FileSelector、RunLogCard / CrashSectionCard、
 *    LogScrollButtons、PullToRefreshBox 均照搬 wekit 适配。
 *
 * @param res StringRes 实例（本页文案硬编码中文，暂不使用，签名与其余设置页对齐保留）。
 * @param onBack 返回上一级（pager 根由 MainPagerScreen 委托）。
 */
@Composable
fun LogsScreen(
    res: StringRes,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val kind = LOG_TABS[selectedTab]
    val pagerState = rememberPagerState(initialPage = selectedTab, pageCount = { LOG_TABS.size })

    // 每个 tab 独立保留一个 LazyListState，刷新后滚动位置不丢（wekit 同款）。
    val runListState = rememberLazyListState()
    val crashListState = rememberLazyListState()

    var refreshGeneration by remember { mutableIntStateOf(0) }
    var pullRefreshingKind by remember { mutableStateOf<LogKind?>(null) }
    // 每个 tab 的文件选择互相独立（两个页面同时保持组合）。
    val currentFiles = remember { mutableStateMapOf<LogKind, Path?>() }
    val currentFile = currentFiles[kind]
    var menuExpanded by remember { mutableStateOf(false) }

    // ---- 保存（CreateDocument）：仅当组合环境能解析到 ComponentActivity 时可用 ----
    // 宿主注入面板跑在 ComponentDialog（非 Activity，无 ActivityResultRegistry），
    // wekit 用自建 TransparentActivity 绕开，本项目不移植，直接降级为隐藏保存按钮。
    val saveActivity = remember {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>()
            .firstOrNull()
    }
    var saveTargetFile by remember { mutableStateOf<Path?>(null) }
    var saveLauncher by remember { mutableStateOf<ActivityResultLauncher<String>?>(null) }
    if (saveActivity != null) {
        val launcher = remember(saveActivity) {
            saveActivity.activityResultRegistry.register(
                "auto_oral_logs_save",
                ActivityResultContracts.CreateDocument("text/plain"),
            ) { uri ->
                val file = saveTargetFile ?: return@register
                if (uri == null) return@register // 用户在系统选择器中取消
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri, "w")!!.use { out ->
                                file.toFile().inputStream().use { it.copyTo(out) }
                            }
                        }.isSuccess
                    }
                    Toast.makeText(context, if (ok) "日志已保存" else "保存日志失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
        saveLauncher = launcher
        DisposableEffect(launcher) {
            onDispose { launcher.unregister() }
        }
    }

    fun requestRefresh(targetKind: LogKind, fromPull: Boolean) {
        refreshGeneration++
        if (fromPull) pullRefreshingKind = targetKind
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            // 顶栏 + 其下 TabRow（对齐 wekit LogsPager 的 TopAppBar bottomContent 布局）。
            Column {
                TopAppBar(
                    title = { Text("日志") },
                    navigationIcon = { M3BackButton(onClick = onBack) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    actions = {
                        // 按钮背景：图标按钮加 surfaceVariant 圆形底色，避免透明不可见（UI 修复）。
                        val actionBtnModifier = Modifier
                            .padding(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                        IconButton(
                            onClick = {
                                val file = currentFile
                                if (file == null) {
                                    Toast.makeText(context, "暂无日志可分享", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch { shareLogFile(context, file) }
                                }
                            },
                            modifier = actionBtnModifier,
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Share,
                                contentDescription = "分享",
                            )
                        }
                        // 保存：宿主面板（ComponentDialog，无 ActivityResultRegistry）也能保存 ——
                        // 降级为复制到模块 filesDir/log-export/ 并 Toast 完整路径（UI 修复）。
                        IconButton(
                            onClick = {
                                val file = currentFile
                                if (file == null) {
                                    Toast.makeText(context, "暂无日志可保存", Toast.LENGTH_SHORT).show()
                                } else if (saveLauncher != null) {
                                    val launcher = saveLauncher!!
                                    saveTargetFile = file
                                    launcher.launch(file.name)
                                } else {
                                    scope.launch { saveLogToModuleDir(context, file) }
                                }
                            },
                            modifier = actionBtnModifier,
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Save,
                                contentDescription = "保存",
                            )
                        }
                        // 溢出菜单锚点（照项目 DropDownMenuWidget 的锚点写法）。
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = actionBtnModifier) {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.More_vert,
                                    contentDescription = "更多",
                                )
                            }
                            DropdownMenuPopup(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                // M3 菜单默认带 surface 背景；顶部操作按钮背景见 actions（已加 surfaceVariant 圆底）
                                DropdownMenuItem(
                                    text = { Text("刷新") },
                                    leadingIcon = { Icon(MaterialSymbols.Outlined.Refresh, null) },
                                    onClick = {
                                        menuExpanded = false
                                        requestRefresh(kind, fromPull = false)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("清除") },
                                    leadingIcon = { Icon(MaterialSymbols.Outlined.Delete_sweep, null) },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                when (kind) {
                                                    LogKind.RUN -> WeLogger.allLogFiles.forEach {
                                                        runCatching { it.toFile().delete() }
                                                    }

                                                    LogKind.CRASH -> CrashLogsManager.deleteAllCrashLogs()
                                                }
                                            }
                                            requestRefresh(kind, fromPull = false)
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        LOG_TABS.forEachIndexed { index, tabKind ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = {
                                    selectedTab = index
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    Text(
                                        if (tabKind == LogKind.RUN) "运行日志" else "崩溃日志",
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = false,
            key = { LOG_TABS[it] },
        ) { page ->
            val k = LOG_TABS[page]
            LogTabContent(
                kind = k,
                listState = if (k == LogKind.RUN) runListState else crashListState,
                innerPadding = innerPadding,
                refreshGeneration = refreshGeneration,
                isPullRefreshing = pullRefreshingKind == k,
                onRefreshRequested = { requestRefresh(k, fromPull = true) },
                onRefreshFinished = { if (pullRefreshingKind == k) pullRefreshingKind = null },
                onCurrentFileChange = { currentFiles[k] = it },
            )
        }
    }
}

/**
 * 单个 tab 的内容：文件列表 + 选中文件读取 + 解析（一个 cancellation-keyed 协程负责
 * 列目录、选择归一化、读文件、解析全流程；列目录只在首次/刷新时执行）。
 */
@Composable
private fun LogTabContent(
    kind: LogKind,
    listState: LazyListState,
    innerPadding: PaddingValues,
    refreshGeneration: Int,
    isPullRefreshing: Boolean,
    onRefreshRequested: () -> Unit,
    onRefreshFinished: () -> Unit,
    onCurrentFileChange: (Path?) -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()

    var files by remember(kind) { mutableStateOf<List<Path>>(emptyList()) }
    var selectedIndex by rememberSaveable(kind) { mutableIntStateOf(0) }
    var runEntries by remember(kind) { mutableStateOf<List<RunLogEntry>>(emptyList()) }
    var crashSections by remember(kind) { mutableStateOf<List<CrashSection>>(emptyList()) }
    var loading by remember(kind) { mutableStateOf(true) }
    var listed by remember(kind) { mutableStateOf(false) }
    var fileReadGeneration by remember(kind) { mutableIntStateOf(0) }
    var handledRefreshGeneration by remember(kind) { mutableIntStateOf(-1) }

    LaunchedEffect(kind, refreshGeneration, fileReadGeneration) {
        var completedSuccessfully = false
        loading = true
        try {
            // 只有首次进入或收到新的刷新请求才重新列目录；换文件只重读不重列。
            if (!listed || refreshGeneration != handledRefreshGeneration) {
                val result = withContext(Dispatchers.IO) {
                    when (kind) {
                        LogKind.RUN -> WeLogger.allLogFiles
                        LogKind.CRASH -> CrashLogsManager.allCrashLogs
                    }
                }
                files = result
                val normalizedIndex = if (result.isEmpty()) 0 else selectedIndex.coerceIn(result.indices)
                if (selectedIndex != normalizedIndex) selectedIndex = normalizedIndex
                listed = true
                handledRefreshGeneration = refreshGeneration
            }

            val selectedFile = files.getOrNull(selectedIndex)
            onCurrentFileChange(selectedFile)
            if (selectedFile == null) {
                runEntries = emptyList()
                crashSections = emptyList()
            } else {
                val text = withContext(Dispatchers.IO) { readLog(selectedFile) }
                when (kind) {
                    LogKind.RUN -> runEntries = withContext(Dispatchers.Default) { parseRunLog(text) }
                    LogKind.CRASH -> crashSections = withContext(Dispatchers.Default) { parseCrashLog(text) }
                }
            }
            completedSuccessfully = true
        } catch (e: CancellationException) {
            throw e
        } finally {
            loading = false
            if (completedSuccessfully) onRefreshFinished()
        }
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing,
        onRefresh = onRefreshRequested,
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = innerPadding.calculateTopPadding()),
                state = pullToRefreshState,
                isRefreshing = isPullRefreshing,
                color = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            item(key = "picker") {
                FileSelector(
                    kind = kind,
                    files = files,
                    selectedIndex = selectedIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0)),
                    onSelected = {
                        selectedIndex = it
                        fileReadGeneration++
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (files.isEmpty()) {
                // 首次列目录完成前不显示空态，避免在加载指示下闪一下「暂无日志」。
                if (listed) {
                    item(key = "empty-files") {
                        LogsEmpty(
                            if (kind == LogKind.RUN) "暂无运行日志" else "暂无崩溃日志",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else when (kind) {
                LogKind.RUN -> {
                    if (runEntries.isEmpty() && !loading) {
                        item(key = "empty-run") {
                            LogsEmpty("文件为空", Modifier.padding(top = 8.dp))
                        }
                    }
                    items(runEntries.size, key = { "run-$it" }) { i ->
                        RunLogCard(
                            runEntries[i],
                            Modifier.padding(top = 8.dp),
                        )
                    }
                }

                LogKind.CRASH -> {
                    if (crashSections.isEmpty() && !loading) {
                        item(key = "empty-crash") {
                            LogsEmpty("文件为空", Modifier.padding(top = 8.dp))
                        }
                    }
                    items(crashSections.size, key = { "crash-$it" }) { i ->
                        CrashSectionCard(
                            crashSections[i],
                            Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            item(key = "bottom-inset") { Spacer(Modifier.height(LOGS_CONTENT_BOTTOM_INSET)) }
        }

        LogScrollButtons(
            listState = listState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = LOGS_BOTTOM_BAR_INSET),
        )
    }
}

// ---------------------------------------------------------------------------
//  文件分享 / 保存
// ---------------------------------------------------------------------------

/** 分享上限（字符）：超过则截断，避免 ACTION_SEND 内联文本触发 Binder 1MB 限制。 */
private const val SHARE_MAX_CHARS = 250_000

/**
 * 分享日志文件：`ACTION_SEND` text/plain 内联文本（本项目无 FileProvider，wekit 的
 * FileProvider 分支不移植）。日志过长时截断并附提示。
 */
private suspend fun shareLogFile(context: Context, file: Path) {
    val text = withContext(Dispatchers.IO) {
        val full = runCatching { file.readText() }.getOrDefault("")
        if (full.length > SHARE_MAX_CHARS) {
            full.take(SHARE_MAX_CHARS) +
                "\n\n[日志过长，已截断分享，请使用「保存」导出完整日志]"
        } else {
            full
        }
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, "分享日志").apply {
        // 宿主注入环境 context 不是 Activity，必须 NEW_TASK。
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
        .onFailure { WeLogger.e(LOGS_TAG, "failed to launch share chooser", it) }
}

/**
 * 宿主面板（ComponentDialog，无 ActivityResultRegistry）下的「保存日志」降级方案：
 * 把当前日志文件复制到模块自身 dataDir/files/log-export/（宿主进程可读写模块私有目录），
 * 并 Toast 完整路径。独立模块场景仍优先走系统 CreateDocument 选择器（saveLauncher）。
 */
private suspend fun saveLogToModuleDir(context: Context, file: Path) {
    val savedPath = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(
                XposedInit.self.moduleApplicationInfo.dataDir,
                "files/log-export"
            ).apply { mkdirs() }
            val target = File(exportDir, file.name)
            File(file.toString()).inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.absolutePath
        }.getOrNull()
    }
    if (savedPath != null) {
        Toast.makeText(context, "日志已保存：$savedPath", Toast.LENGTH_LONG).show()
    } else {
        Toast.makeText(context, "保存日志失败", Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------------------
//  解析（照搬 wekit LogsPager）
// ---------------------------------------------------------------------------

/** 哪个 tab 在展示哪种日志。 */
private enum class LogKind { RUN, CRASH }

/** 一条运行日志条目：头行 + 折叠进 message 的续行（堆栈等）。 */
private data class RunLogEntry(
    val time: String?,
    val level: Char?,
    val tag: String?,
    val message: String,
)

/** 一个崩溃报告 section：「==== 标题 ====」块及其下正文。 */
private data class CrashSection(
    val title: String,
    val body: String,
)

// WeLogger 每行写入格式："$ts $level/$TAG $tag: $msg"
//   ts    = yyyy-MM-dd HH:mm:ss.SSS
//   level = V D I W E A 之一
//   $TAG  = AutoOral（模块 tag），$tag = 调用方 tag
// 例： "2026-07-05 14:30:22.123 E/AutoOral CrashLogsManager: something failed"
// Groups: 1=date 2=time(+ms) 3=level 4=moduleTag 5=callerTag 6=message
private val RUN_LOG_REGEX = Regex(
    """^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}\.\d{3}) ([VDIWEAF])/(\S+)\s+([^:]*): (.*)$""",
)

/**
 * 把原始运行日志解析成 [RunLogEntry] 卡片。命中 [RUN_LOG_REGEX] 的行开新卡片；
 * 其他行（堆栈续行、多行消息）折叠进上一张卡片；开头孤立行成为无元数据卡片。
 */
private fun parseRunLog(text: String): List<RunLogEntry> {
    val out = ArrayList<RunLogEntry>()
    for (line in text.lineSequence()) {
        if (line.isEmpty() && out.isEmpty()) continue
        val m = RUN_LOG_REGEX.matchEntire(line)
        when {
            m != null -> {
                val (_, time, level, _, tag, msg) = m.destructured
                out.add(
                    RunLogEntry(
                        time = time,
                        level = level.firstOrNull(),
                        tag = tag.trim().ifEmpty { null },
                        message = msg,
                    ),
                )
            }

            out.isNotEmpty() -> {
                val prev = out.removeAt(out.size - 1)
                out.add(prev.copy(message = if (prev.message.isEmpty()) line else prev.message + "\n" + line))
            }

            else -> out.add(RunLogEntry(time = null, level = null, tag = null, message = line))
        }
    }
    return out
}

/**
 * 把崩溃报告切成 [CrashSection] 卡片。报告是「====」fence 块序列：fence / 标题 /
 * fence / 正文，直到下一个 fence。首块之前的序言成为无标题卡片。
 */
private fun parseCrashLog(text: String): List<CrashSection> {
    val lines = text.lines()
    val fence = "========================================"
    val out = ArrayList<CrashSection>()

    var i = 0
    val preamble = StringBuilder()
    while (i < lines.size && !(lines[i] == fence && i + 2 < lines.size && lines[i + 2] == fence)) {
        preamble.appendLine(lines[i]); i++
    }
    val pre = preamble.toString().trim()
    if (pre.isNotEmpty()) out.add(CrashSection(title = "", body = pre))

    while (i < lines.size) {
        if (lines[i] == fence && i + 2 < lines.size && lines[i + 2] == fence) {
            val title = lines[i + 1].trim()
            i += 3
            val body = StringBuilder()
            while (i < lines.size && !(lines[i] == fence && i + 2 < lines.size && lines[i + 2] == fence)) {
                body.appendLine(lines[i]); i++
            }
            out.add(CrashSection(title = title, body = body.toString().trim()))
        } else {
            i++
        }
    }
    return out
}

/** 读取完整日志文件（现代设备读多 MB 日志没问题）。 */
private fun readLog(file: Path): String =
    runCatching { file.readText() }
        .getOrElse { "读取日志失败：${it.message}" }

// ---------------------------------------------------------------------------
//  格式工具（项目内无，照搬 wekit FormatUtils）
// ---------------------------------------------------------------------------

private fun formatBytesSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return "%.2f %s".format(value, units[digitGroups])
}

private fun formatEpoch(epochMs: Long, pattern: String): String {
    val formatter =
        DateTimeFormatter.ofPattern(pattern)
            .withZone(ZoneId.of("Asia/Shanghai"))
            .withLocale(Locale.CHINA)
    return formatter.format(Instant.ofEpochMilli(epochMs))
}

private fun formatEpoch(epochMs: Long, includeDate: Boolean): String =
    formatEpoch(epochMs, if (includeDate) "yyyy/MM/dd HH:mm:ss" else "HH:mm:ss")

// ---------------------------------------------------------------------------
//  布局常量
// ---------------------------------------------------------------------------

/** 悬浮底栏高度余量：滚顶/滚底按钮避开底部悬浮胶囊底栏。 */
private val LOGS_BOTTOM_BAR_INSET = 88.dp

/** 内容底部留白：最后一张卡片不被悬浮底栏遮挡。 */
private val LOGS_CONTENT_BOTTOM_INSET = 184.dp

/** 滚动距离超过该值时用瞬时跳转而非动画（长列表动画会卡顿）。 */
private const val LOG_SCROLL_ANIMATION_THRESHOLD = 500

private val LOG_TABS = listOf(LogKind.RUN, LogKind.CRASH)

// ---------------------------------------------------------------------------
//  文件选择器 + 卡片 + 空态（照搬 wekit LogsPager）
// ---------------------------------------------------------------------------

@Composable
private fun FileSelector(
    kind: LogKind,
    files: List<Path>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) return
    fun displayDate(file: Path): String = when (kind) {
        LogKind.RUN -> formatEpoch(file.getLastModifiedTime().toMillis(), "yyyy/MM/dd")
        LogKind.CRASH -> formatEpoch(file.getLastModifiedTime().toMillis(), includeDate = true)
    }
    val labels = remember(kind, files) {
        files.map { file ->
            val date = runCatching { displayDate(file) }.getOrDefault("")
            val size = runCatching { formatBytesSize(file.fileSize()) }.getOrDefault("0 B")
            "$date  ·  $size"
        }
    }
    Box(modifier.fillMaxWidth()) {
        DropDownMenuWidget(
            title = "选择日志文件",
            description = files.getOrNull(selectedIndex)?.let { displayDate(it) },
            value = selectedIndex,
            options = labels.mapIndexed { index, label -> DropdownOption(index, label) },
            onValueChange = onSelected,
        )
    }
}

/** 长消息（超过该行数）折叠为预览 + 展开按钮。 */
private const val RUN_LOG_COLLAPSE_LINES = 5

/** 崩溃 section 超过该行数折叠为预览。 */
private const val CRASH_SECTION_COLLAPSE_LINES = 12

@Composable
private fun RunLogCard(entry: RunLogEntry, modifier: Modifier = Modifier) {
    val lines = remember(entry.message) { entry.message.split("\n") }
    val isLong = lines.size > RUN_LOG_COLLAPSE_LINES
    val head = remember(lines) { lines.take(RUN_LOG_COLLAPSE_LINES).joinToString("\n") }
    val rest = remember(lines) { lines.drop(RUN_LOG_COLLAPSE_LINES).joinToString("\n") }
    var expanded by remember(entry) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "chevron",
    )

    BaseItemContainer(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                entry.level?.let { level ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(levelColor(level))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(level.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    entry.tag?.let {
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    entry.time?.let {
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isLong) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Expand_more,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation),
                        )
                    }
                }
            }
            if (entry.message.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Column {
                        Text(
                            text = if (isLong) head else entry.message,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isLong) {
                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Text(
                                    text = rest,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashSectionCard(section: CrashSection, modifier: Modifier = Modifier) {
    val lines = remember(section.body) { section.body.split("\n") }
    val isLong = lines.size > CRASH_SECTION_COLLAPSE_LINES
    val head = remember(lines) { lines.take(CRASH_SECTION_COLLAPSE_LINES).joinToString("\n") }
    val rest = remember(lines) { lines.drop(CRASH_SECTION_COLLAPSE_LINES).joinToString("\n") }
    var expanded by rememberSaveable(section.title, section.body) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "crashChevron",
    )

    BaseItemContainer(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (section.title.isNotEmpty() || isLong) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (section.title.isNotEmpty()) {
                        Text(
                            text = section.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (isLong) {
                        IconButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Expand_more,
                                contentDescription = if (expanded) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(chevronRotation),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            SelectionContainer {
                Column {
                    Text(
                        text = if (isLong) head else section.body,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isLong) {
                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Text(
                                text = rest,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsEmpty(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 日志级别色块背景（对应 WeLogger 写出的级别字符）。 */
private fun levelColor(level: Char): Color = when (level) {
    'E', 'F', 'A' -> Color(0xFFD32F2F)
    'W' -> Color(0xFFF57C00)
    'I' -> Color(0xFF388E3C)
    'D' -> Color(0xFF1976D2)
    'V' -> Color(0xFF757575)
    else -> Color(0xFF9E9E9E)
}

// ---------------------------------------------------------------------------
//  滚顶 / 滚底按钮（照搬 wekit LogsPager）
// ---------------------------------------------------------------------------

@Composable
private fun LogScrollButtons(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.canScrollBackward }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        if (listState.firstVisibleItemIndex > LOG_SCROLL_ANIMATION_THRESHOLD) {
                            listState.scrollToItem(0)
                        } else {
                            listState.animateScrollToItem(0)
                        }
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Keyboard_double_arrow_up,
                    contentDescription = "回顶部",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        SmallFloatingActionButton(
            onClick = {
                scope.launch {
                    val end = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    if (end - listState.firstVisibleItemIndex > LOG_SCROLL_ANIMATION_THRESHOLD) {
                        listState.scrollToItem(end)
                    } else {
                        listState.animateScrollToItem(end)
                    }
                }
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Keyboard_double_arrow_down,
                contentDescription = "回底部",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
