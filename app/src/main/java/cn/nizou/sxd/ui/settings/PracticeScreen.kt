package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.ui.components.M3ListScaffold
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.ui.components.SwitchWidget
import cn.nizou.sxd.ui.components.TextFieldDialogWidget
import cn.nizou.sxd.util.SettingsPrefs
import cn.nizou.sxd.util.StringRes

/** 练习设置页：口算练习自动答题。 */
@Composable
fun PracticeScreen(res: StringRes, onBack: () -> Unit) {
    var autoHonor by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_AUTO_HONOR, false))
    }
    var autoPractice by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_AUTO_PRACTICE, true))
    }
    var autoPracticeQuick by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_AUTO_PRACTICE_QUICK, false))
    }
    var autoPracticeCyclic by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_AUTO_PRACTICE_CYCLIC, false))
    }
    var autoPracticeCyclicInterval by remember {
        mutableStateOf(SettingsPrefs.readString(res, res.KEY_AUTO_PRACTICE_CYCLIC_INTERVAL, "1500"))
    }

    M3ListScaffold(
        title = "练习",
        navigationIcon = { M3BackButton(onClick = onBack) }
    ) {
        item {
            SegmentedColumn(title = "练习") {
                SwitchWidget(
                    title = "自动上分",
                    description = "进入任意口算练习页面挂机即可",
                    checked = autoHonor,
                    onCheckedChange = {
                        autoHonor = it
                        SettingsPrefs.writeBoolean(res, res.KEY_AUTO_HONOR, it)
                    }
                )
                SwitchWidget(
                    title = "练习场自动答题",
                    enabled = autoHonor,
                    checked = autoPractice,
                    onCheckedChange = {
                        autoPractice = it
                        SettingsPrefs.writeBoolean(res, res.KEY_AUTO_PRACTICE, it)
                    }
                )
                SwitchWidget(
                    title = "极速答题",
                    enabled = autoHonor && autoPractice,
                    checked = autoPracticeQuick,
                    onCheckedChange = {
                        autoPracticeQuick = it
                        SettingsPrefs.writeBoolean(res, res.KEY_AUTO_PRACTICE_QUICK, it)
                    }
                )
                SwitchWidget(
                    title = "循环练习",
                    enabled = autoHonor && autoPractice,
                    checked = autoPracticeCyclic,
                    onCheckedChange = {
                        autoPracticeCyclic = it
                        SettingsPrefs.writeBoolean(res, res.KEY_AUTO_PRACTICE_CYCLIC, it)
                    }
                )
                TextFieldDialogWidget(
                    title = "循环时间间隔",
                    value = autoPracticeCyclicInterval,
                    placeholder = "单位毫秒，默认值1500",
                    enabled = autoHonor && autoPractice && autoPracticeCyclic,
                    keyboardType = KeyboardType.Number,
                    filter = { it.filter { c -> c.isDigit() } },
                    onValueChange = {
                        autoPracticeCyclicInterval = it
                        SettingsPrefs.writeString(res, res.KEY_AUTO_PRACTICE_CYCLIC_INTERVAL, it)
                    }
                )
            }
        }
        item { Box(Modifier.padding(24.dp)) {} }
    }
}
