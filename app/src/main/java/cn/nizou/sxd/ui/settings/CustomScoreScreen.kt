package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.api.LegacyApiService
import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.logI
import cn.nizou.sxd.util.mainHandler

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
 * 自定义分数面板（替代旧 SettingHook.showCustomScoreDialog 的悬浮 AlertDialog）。
 * 逻辑与旧实现一致：读取当前分数 → 输入目标分数 → 提交刷分（postSavedExp）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomScoreScreen(onBack: () -> Unit) {
    var loadState by remember { mutableStateOf<ScoreLoadState>(ScoreLoadState.Loading) }
    var target by remember { mutableStateOf("") }
    var suppose by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    val currentScore = (loadState as? ScoreLoadState.Success)?.score

    fun updateCurrentScore() {
        if (!LegacyApiService.isReady()) {
            // 模块本体独立运行：未注入宿主，无法初始化 LegacyApiService。
            // 明确报错，而不是永久「加载中」。
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
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
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
                text = when (loadState) {
                    ScoreLoadState.Loading -> "当前分数：加载中"
                    ScoreLoadState.Uninitialized -> "当前分数：未初始化"
                    is ScoreLoadState.Error -> "当前分数：读取失败"
                    is ScoreLoadState.Success -> "当前分数：${loadState.score}"
                },
                style = MaterialTheme.typography.bodyLarge
            )
            when (loadState) {
                ScoreLoadState.Uninitialized -> Text(
                    text = "当前为模块本体独立运行，未接入宿主 ApiService，无法读取分数。\n" +
                        "请在小猿口算（宿主）内打开「老挂戏老叟设置」使用此功能。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                is ScoreLoadState.Error -> Text(
                    text = "读取分数失败：${loadState.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> Unit
            }
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
                    LegacyApiService.postSavedExp(obtain.toInt()) {
                        it.onSuccess {
                            submitting = false
                            updateCurrentScore()
                        }.onFailure { th ->
                            submitting = false
                            logI(th)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (submitting) "提交中…" else "确认")
            }
        }
    }
}
