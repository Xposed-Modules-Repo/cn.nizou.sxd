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
 * 自定义分数面板（替代旧 SettingHook.showCustomScoreDialog 的悬浮 AlertDialog）。
 * 逻辑与旧实现一致：读取当前分数 → 输入目标分数 → 提交刷分（postSavedExp）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomScoreScreen(onBack: () -> Unit) {
    var currentScore by remember { mutableStateOf<Int?>(null) }
    var target by remember { mutableStateOf("") }
    var suppose by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    fun updateCurrentScore() {
        LegacyApiService.getCurrentUserExp {
            it.onSuccess { data ->
                val curWeekScore = XposedHelpers.getIntField(data, "curWeekScore")
                logI("curWeekScore: $curWeekScore")
                mainHandler.post {
                    currentScore = curWeekScore
                    suppose = null
                }
            }.onFailure { th -> logI(th) }
        }
    }

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
                text = "当前分数：${currentScore?.toString() ?: "加载中"}",
                style = MaterialTheme.typography.bodyLarge
            )
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
