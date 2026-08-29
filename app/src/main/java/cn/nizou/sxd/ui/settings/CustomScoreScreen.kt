package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.api.LegacyApiService
import cn.nizou.sxd.util.SettingsPrefs
import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.logI
import cn.nizou.sxd.util.mainHandler
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back

/**
 * 当前分数加载状态机。
 *
 * 根因修复：LegacyApiService 只在宿主进程由 SettingHook.hookHomeActivity 的 init()+setup()
 * 初始化；模块独立 App（模块本体）是**独立进程**，不注入宿主，apiService/coroutineContext
 * 从未初始化 → 旧代码卡死在「加载中」。这里在调用前先用 [LegacyApiService.isReady] 探测：
 * 未初始化（模块本体）→ 明确错误提示；已初始化（宿主注入面板）→ 正常拉取。
 */
private sealed interface ScoreLoadState {
    /** 加载中（仅在宿主内、已初始化后短暂出现） */
    data object Loading : ScoreLoadState

    /** 模块本体独立运行：LegacyApiService 未初始化，无法读宿主分数 */
    data object Uninitialized : ScoreLoadState

    data class Success(val score: Int) : ScoreLoadState

    data class Error(val message: String) : ScoreLoadState
}

/**
 * 自定义分数面板。
 *
 * 两种模式（prefs `custom_score_mode`，0=刷分 / 1=真自定义）：
 * - 刷分模式（保留旧逻辑）：输入增量，单条 todayExercises 提交获得经验（服务端单条 obtainExp
 *   有上限 ~200，且一天刷几次后会被服务端拒绝）。
 * - 真自定义分数：输入**目标分数**，按 [LegacyApiService.postSavedExpBatch] 一次提交多条
 *   todayExercises（每条 ≤ 单条上限，finishTime 错开模拟真实做题），把 curWeekScore 直接顶到
 *   目标值 —— 绕过「单次 200 / 刷几次就刷不了」的限制（前提：服务端不校验单次请求总增量）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomScoreScreen(onBack: () -> Unit) {
    var loadState by remember { mutableStateOf<ScoreLoadState>(ScoreLoadState.Loading) }
    var scoreMode by remember {
        mutableIntStateOf(SettingsPrefs.readInt("custom_score_mode", 0).coerceIn(0, 1))
    }
    var target by remember { mutableStateOf("") }
    var directTarget by remember { mutableStateOf("") }
    var perItem by remember {
        mutableStateOf(SettingsPrefs.readInt("custom_score_per_item", 200).toString())
    }
    var suppose by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }

    val currentScore = (loadState as? ScoreLoadState.Success)?.score

    fun updateCurrentScore() {
        if (!LegacyApiService.isReady()) {
            loadState = ScoreLoadState.Uninitialized
            return
        }
        loadState = ScoreLoadState.Loading
        LegacyApiService.getCurrentUserExp {
            it.onSuccess { data ->
                val curWeekScore = XposedHelpers.getIntField(data, "curWeekScore")
                logI("curWeekScore: $curWeekScore")
                mainHandler.post {
                    loadState = ScoreLoadState.Success(curWeekScore)
                    suppose = null
                    resultMsg = null
                }
            }.onFailure { th ->
                logI(th)
                mainHandler.post {
                    loadState = ScoreLoadState.Error(th.message ?: th.toString())
                }
            }
        }
    }

    LaunchedEffect(Unit) { updateCurrentScore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义分数") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Arrow_back,
                            contentDescription = "返回",
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = when (val s = loadState) {
                    ScoreLoadState.Loading -> "当前分数：加载中"
                    ScoreLoadState.Uninitialized -> "当前分数：未初始化"
                    is ScoreLoadState.Error -> "当前分数：读取失败"
                    is ScoreLoadState.Success -> "当前分数：${s.score}"
                },
                style = MaterialTheme.typography.bodyLarge
            )
            when (val s = loadState) {
                ScoreLoadState.Uninitialized -> Text(
                    text = "当前为模块本体独立运行，未接入宿主 ApiService，无法读取分数。\n" +
                        "请在小猿口算（宿主）内打开「老挂戏老叟设置」使用此功能。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                is ScoreLoadState.Error -> Text(
                    text = "读取分数失败：${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> Unit
            }

            Text("模式", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scoreMode = 0
                        SettingsPrefs.writeInt("custom_score_mode", 0)
                    }
            ) {
                RadioButton(
                    selected = scoreMode == 0,
                    onClick = {
                        scoreMode = 0
                        SettingsPrefs.writeInt("custom_score_mode", 0)
                    }
                )
                Column {
                    Text("刷分模式（增量）", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "每次 +N 经验。服务端单次上限约 200，一天刷几次后会被拒绝。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scoreMode = 1
                        SettingsPrefs.writeInt("custom_score_mode", 1)
                    }
            ) {
                RadioButton(
                    selected = scoreMode == 1,
                    onClick = {
                        scoreMode = 1
                        SettingsPrefs.writeInt("custom_score_mode", 1)
                    }
                )
                Column {
                    Text("真自定义分数（一次到位）", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "直接设置目标分数，一次提交多条记录顶到目标值，绕过单次 200 限制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (scoreMode == 0) {
                // ---- 刷分模式（保留旧逻辑）----
                Text(
                    text = if (suppose != null) "预计目标分数：$suppose" else "预计目标分数：${currentScore?.toString() ?: "加载中"}",
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = {
                        target = it.filter { c -> c.isDigit() || c == '-' }
                        val cur = currentScore
                        val obtain = target.toLongOrNull()
                            ?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                        suppose = if (cur != null && obtain != null) (cur + obtain.toInt()).toString() else null
                    },
                    label = { Text("请输入刷取分数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = currentScore != null && target.toLongOrNull() != null && !submitting,
                    onClick = {
                        val cur = currentScore ?: return@Button
                        val obtain = target.toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                        target = ""
                        submitting = true
                        resultMsg = null
                        LegacyApiService.postSavedExp(obtain.toInt()) {
                            submitting = false
                            it.onSuccess {
                                updateCurrentScore()
                            }.onFailure { th ->
                                logI(th)
                                resultMsg = "提交失败：${th.message ?: th}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (submitting) "提交中…" else "确认刷分")
                }
            } else {
                // ---- 真自定义分数：一次设置目标 ----
                val perItemV = perItem.toIntOrNull()?.coerceAtLeast(1) ?: 200
                val diff = if (currentScore != null && directTarget.toIntOrNull() != null) {
                    directTarget.toInt() - currentScore
                } else {
                    null
                }
                val items = if (diff != null && diff > 0) {
                    diff / perItemV + if (diff % perItemV != 0) 1 else 0
                } else {
                    0
                }
                Text(
                    text = when {
                        diff == null -> "输入目标分数（须大于当前分数）"
                        diff <= 0 -> "目标分数必须大于当前分数"
                        else -> "差值 +$diff → 一次提交 $items 条记录（每条约 $perItemV 经验）"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (diff != null && diff > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    value = directTarget,
                    onValueChange = {
                        directTarget = it.filter { c -> c.isDigit() }
                        resultMsg = null
                    },
                    label = { Text("目标分数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = perItem,
                    onValueChange = { perItem = it.filter { c -> c.isDigit() } },
                    label = { Text("单条经验上限（默认 200）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = currentScore != null && diff != null && diff > 0 && !submitting,
                    onClick = {
                        val d = diff ?: return@Button
                        submitting = true
                        resultMsg = null
                        LegacyApiService.postSavedExpBatch(d, perItemV) {
                            submitting = false
                            it.onSuccess {
                                updateCurrentScore()
                                resultMsg = "成功：已提交 $items 条记录（+$d 经验）"
                            }.onFailure { th ->
                                logI(th)
                                resultMsg = "提交失败：${th.message ?: th}\n（若被服务端拒绝，可能是单次请求总增量/每日上限，请调低单条上限或分天）"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (submitting) "提交中…" else "直接设置为目标分数")
                }
                Text(
                    text = "说明：拆成多条记录一次提交（finishTime 错开模拟真实做题），绕过服务端「单条 200 / 每天几次」限制。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            resultMsg?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("成功")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
