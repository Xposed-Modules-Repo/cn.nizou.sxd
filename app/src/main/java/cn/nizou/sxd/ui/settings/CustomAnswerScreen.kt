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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.ui.components.M3ListScaffold
import cn.nizou.sxd.ui.components.RadioButtonWidget
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.ui.components.SwitchWidget
import cn.nizou.sxd.ui.components.TextFieldDialogWidget
import cn.nizou.sxd.util.SettingsPrefs
import cn.nizou.sxd.util.StringRes

/**
 * 自定义答案页（Simian 改题目/改答案/口算答案 UI 开关）。
 * 对应 SimianHook 的 4 个 hook 点与 prefs（modify_answer / modify_title / simian_mode /
 * custom_answers / custom_title / practice_answer / vip），读写均走 SettingsPrefs 同一批键。
 */
@Composable
fun CustomAnswerScreen(res: StringRes, onBack: () -> Unit) {
    var modifyAnswer by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_MODIFY_ANSWER, false))
    }
    var modifyTitle by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_MODIFY_TITLE, false))
    }
    var modeIndex by remember {
        mutableIntStateOf(
            runCatching {
                SettingsPrefs.readString(res, res.KEY_SIMIAN_MODE, "0").toInt()
            }.getOrDefault(0).coerceIn(0, 1)
        )
    }
    var customAnswers by remember {
        mutableStateOf(SettingsPrefs.readString(res, res.KEY_CUSTOM_ANSWERS, "1"))
    }
    var customTitle by remember {
        mutableStateOf(SettingsPrefs.readString(res, res.KEY_CUSTOM_TITLE, "78+13=\\square"))
    }
    var customQuestionCount by remember {
        mutableStateOf(SettingsPrefs.readString(res, res.KEY_CUSTOM_QUESTION_COUNT, "0"))
    }
    var practiceAnswer by remember {
        mutableStateOf(SettingsPrefs.readString(res, res.KEY_PRACTICE_ANSWER, "1"))
    }
    var vip by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_VIP, false))
    }

    // 单题模式（mode=1）与改题目开关互斥：mode=1 强制单题改题目+答案。
    val singleMode = modeIndex == 1
    val titleEnabled = singleMode || modifyTitle

    M3ListScaffold(
        title = "自定义答案",
        navigationIcon = { M3BackButton(onClick = onBack) }
    ) {
        item {
            SegmentedColumn(title = "改答案") {
                SwitchWidget(
                    title = "改答案",
                    description = "多题模式：所有题答案改为自定义答案",
                    checked = modifyAnswer,
                    onCheckedChange = {
                        modifyAnswer = it
                        SettingsPrefs.writeBoolean(res, res.KEY_MODIFY_ANSWER, it)
                    }
                )
                TextFieldDialogWidget(
                    title = "口算答案",
                    value = customAnswers,
                    placeholder = "自定义答案内容",
                    onValueChange = {
                        customAnswers = it
                        SettingsPrefs.writeString(res, res.KEY_CUSTOM_ANSWERS, it)
                    }
                )
            }
        }

        item {
            SegmentedColumn(title = "改题目") {
                SwitchWidget(
                    title = "改题目",
                    description = "单题模式：只保留最后一题并改题目与答案",
                    checked = modifyTitle,
                    onCheckedChange = {
                        modifyTitle = it
                        SettingsPrefs.writeBoolean(res, res.KEY_MODIFY_TITLE, it)
                    }
                )
                TextFieldDialogWidget(
                    title = "单题题目",
                    value = customTitle,
                    placeholder = "要改成的题目内容",
                    enabled = titleEnabled,
                    onValueChange = {
                        customTitle = it
                        SettingsPrefs.writeString(res, res.KEY_CUSTOM_TITLE, it)
                    }
                )
            }
        }

        item {
            SegmentedColumn(title = "模式") {
                listOf("多题改答案" to 0, "单题改题目+答案" to 1).forEach { (label, idx) ->
                    RadioButtonWidget(
                        title = label,
                        selected = modeIndex == idx,
                        onSelect = {
                            modeIndex = idx
                            SettingsPrefs.writeString(res, res.KEY_SIMIAN_MODE, idx.toString())
                        }
                    )
                }
            }
        }

        item {
            SegmentedColumn(title = "题目数量") {
                TextFieldDialogWidget(
                    title = "自定义题目数量",
                    value = customQuestionCount,
                    placeholder = "0=不限制；改题目模式默认1，多题模式取前N题",
                    keyboardType = KeyboardType.Number,
                    filter = { it.filter { c -> c.isDigit() } },
                    onValueChange = {
                        customQuestionCount = it
                        SettingsPrefs.writeString(res, res.KEY_CUSTOM_QUESTION_COUNT, it)
                    }
                )
            }
        }

        item {
            SegmentedColumn(title = "口算练习") {
                TextFieldDialogWidget(
                    title = "口算练习答案",
                    value = practiceAnswer,
                    placeholder = "进入口算练习时每题显示该答案",
                    onValueChange = {
                        practiceAnswer = it
                        SettingsPrefs.writeString(res, res.KEY_PRACTICE_ANSWER, it)
                    }
                )
            }
        }

        item {
            SegmentedColumn(title = "VIP") {
                SwitchWidget(
                    title = "解锁 VIP",
                    description = "UserVipVO.getVipSymbol 短路返回 true",
                    checked = vip,
                    onCheckedChange = {
                        vip = it
                        SettingsPrefs.writeBoolean(res, res.KEY_VIP, it)
                    }
                )
            }
        }

        item { Box(Modifier.padding(24.dp)) {} }
    }
}
