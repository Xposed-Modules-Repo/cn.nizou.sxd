package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.entities.AutoAnswerMode
import cn.nizou.sxd.ui.components.BaseWidget
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.ui.components.M3ListScaffold
import cn.nizou.sxd.ui.components.RadioButtonWidget
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.ui.components.SwitchWidget
import cn.nizou.sxd.ui.components.TextFieldDialogWidget
import cn.nizou.sxd.util.SettingsPrefs
import cn.nizou.sxd.util.StringRes

/** PK 设置页：极速/PK 自动答题。 */
@Composable
fun PkScreen(res: StringRes, onBack: () -> Unit) {
    var autoAnswerConfigIndex by remember {
        mutableIntStateOf(
            runCatching {
                SettingsPrefs.readString(res, res.KEY_AUTO_ANSWER_CONFIG, "0").toInt()
            }.getOrDefault(0)
        )
    }
    var customAnswerConfig by remember {
        mutableStateOf(SettingsPrefs.readString(res, res.KEY_CUSTOM_ANSWER_CONFIG, ""))
    }
    var quickModeMustWin by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_QUICK_MODE_MUST_WIN, false))
    }
    var quickModeInterval by remember {
        mutableStateOf(SettingsPrefs.readString(res, res.KEY_QUICK_MODE_INTERVAL, "200"))
    }
    var pkCyclic by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_PK_CYCLIC, false))
    }
    var pkCyclicInterval by remember {
        mutableStateOf(SettingsPrefs.readString(res, res.KEY_PK_CYCLIC_INTERVAL, "1500"))
    }

    val mode = AutoAnswerMode.entries.getOrElse(autoAnswerConfigIndex) { AutoAnswerMode.DISABLE }

    M3ListScaffold(
        title = "PK",
        navigationIcon = { M3BackButton(onClick = onBack) }
    ) {
        item {
            SegmentedColumn(title = "PK") {
                AutoAnswerMode.entries.forEach { m ->
                    RadioButtonWidget(
                        title = m.value,
                        selected = mode == m,
                        onSelect = {
                            autoAnswerConfigIndex = m.ordinal
                            SettingsPrefs.writeString(res, res.KEY_AUTO_ANSWER_CONFIG, m.ordinal.toString())
                        }
                    )
                }
                TextFieldDialogWidget(
                    title = "自定义答题",
                    value = customAnswerConfig,
                    placeholder = "请输入自定义的js代码",
                    enabled = mode == AutoAnswerMode.CUSTOM,
                    singleLine = false,
                    onValueChange = {
                        customAnswerConfig = it
                        SettingsPrefs.writeString(res, res.KEY_CUSTOM_ANSWER_CONFIG, it)
                    }
                )
                SwitchWidget(
                    title = "秒结算稳赢",
                    enabled = mode == AutoAnswerMode.QUICK,
                    checked = quickModeMustWin,
                    onCheckedChange = {
                        quickModeMustWin = it
                        SettingsPrefs.writeBoolean(res, res.KEY_QUICK_MODE_MUST_WIN, it)
                    }
                )
                TextFieldDialogWidget(
                    title = "秒结算模拟答题间隔",
                    value = quickModeInterval,
                    placeholder = "单位毫秒，默认值200",
                    enabled = mode == AutoAnswerMode.QUICK,
                    keyboardType = KeyboardType.Number,
                    filter = { it.filter { c -> c.isDigit() } },
                    onValueChange = {
                        quickModeInterval = it
                        SettingsPrefs.writeString(res, res.KEY_QUICK_MODE_INTERVAL, it)
                    }
                )

                SwitchWidget(
                    title = "循环PK",
                    enabled = mode == AutoAnswerMode.QUICK || mode == AutoAnswerMode.STANDARD,
                    checked = pkCyclic,
                    onCheckedChange = {
                        pkCyclic = it
                        SettingsPrefs.writeBoolean(res, res.KEY_PK_CYCLIC, it)
                    }
                )
                TextFieldDialogWidget(
                    title = "循环时间间隔",
                    value = pkCyclicInterval,
                    placeholder = "单位毫秒，默认值1500",
                    enabled = pkCyclic,
                    keyboardType = KeyboardType.Number,
                    filter = { it.filter { c -> c.isDigit() } },
                    onValueChange = {
                        pkCyclicInterval = it
                        SettingsPrefs.writeString(res, res.KEY_PK_CYCLIC_INTERVAL, it)
                    }
                )
                SwitchWidget(
                    title = "秒结算",
                    description = "进局自动答题+提交：环境加速（动画0s/静音/自动画线/跳题0ms），移植 2026 秒答题方案",
                    enabled = mode == AutoAnswerMode.QUICK,
                    checked = mode == AutoAnswerMode.QUICK,
                    onCheckedChange = { }
                )
                if (mode == AutoAnswerMode.QUICK) {
                    BaseWidget(
                        title = "提示",
                        description = "「秒结算」模式 = 进局自动答完并提交，无需手动操作；配合循环PK可连续刷局",
                    )
                }
            }
        }
        item { Box(Modifier.padding(24.dp)) {} }
    }
}
