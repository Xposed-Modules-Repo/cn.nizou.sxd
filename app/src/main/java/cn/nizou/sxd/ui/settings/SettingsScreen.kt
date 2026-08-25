package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.BuildConfig
import cn.nizou.sxd.entities.AutoAnswerMode
import cn.nizou.sxd.ui.components.BaseWidget
import cn.nizou.sxd.ui.components.RadioButtonWidget
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.ui.components.SwitchWidget
import cn.nizou.sxd.ui.components.TextFieldDialogWidget
import cn.nizou.sxd.util.SettingsPrefs
import cn.nizou.sxd.util.StringRes
import cn.nizou.sxd.util.openGithub

/**
 * 完整设置页（覆盖 res/xml/host_settings.xml 全部开关：通用/练习/PK/Debug/关于）。
 * 独立模块 UI 与宿主注入面板共用此 Composable。
 *
 * @param res StringRes 实例：独立 MainActivity 传 `StringRes(resources)`（模块自身资源）；
 *            宿主注入面板传 `StringRes(XposedInit.moduleRes)`（模块 APK 经 addAssetPath 加载）。
 * @param onBack 返回/关闭回调（独立页 finish()，宿主面板移除 ComposeView）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    res: StringRes,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // ---- 状态：全部直接绑定 SharedPrefs（即时生效） ----
    var alwaysTrue by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_ALWAYS_TRUE_ANSWER, true))
    }
    var doubleNickname by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_DOUBLE_NICKNAME_LENGTH, true))
    }
    var removeRestriction by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_REMOVE_RESTRICTION_ON_NICKNAME, false))
    }

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

    var debug by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_DEBUG, false))
    }

    val mode = AutoAnswerMode.entries.getOrElse(autoAnswerConfigIndex) { AutoAnswerMode.DISABLE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("口算糕手设置") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            item { SegmentedColumn(title = "通用") {
                SwitchWidget(
                    title = "一切输入视为正确答案",
                    description = "手写输入识别结果永远为正确答案",
                    checked = alwaysTrue,
                    onCheckedChange = {
                        alwaysTrue = it
                        SettingsPrefs.writeBoolean(res, res.KEY_ALWAYS_TRUE_ANSWER, it)
                    }
                )
                SwitchWidget(
                    title = "双倍昵称长度",
                    checked = doubleNickname,
                    onCheckedChange = {
                        doubleNickname = it
                        SettingsPrefs.writeBoolean(res, res.KEY_DOUBLE_NICKNAME_LENGTH, it)
                    }
                )
                SwitchWidget(
                    title = "解除昵称字符限制",
                    description = "开启后，昵称可以使用任意非空白字符",
                    checked = removeRestriction,
                    onCheckedChange = {
                        removeRestriction = it
                        SettingsPrefs.writeBoolean(res, res.KEY_REMOVE_RESTRICTION_ON_NICKNAME, it)
                    }
                )
            } }

            item { SegmentedColumn(title = "练习") {
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
            } }

            item { SegmentedColumn(title = "PK") {
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
                    title = "极速模式稳赢",
                    enabled = mode == AutoAnswerMode.QUICK,
                    checked = quickModeMustWin,
                    onCheckedChange = {
                        quickModeMustWin = it
                        SettingsPrefs.writeBoolean(res, res.KEY_QUICK_MODE_MUST_WIN, it)
                    }
                )
                TextFieldDialogWidget(
                    title = "极速模式模拟答题间隔",
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
            } }

            item { SegmentedColumn(title = "Debug") {
                SwitchWidget(
                    title = "DEBUG",
                    description = "没事别开",
                    checked = debug,
                    onCheckedChange = {
                        debug = it
                        SettingsPrefs.writeBoolean(res, res.KEY_DEBUG, it)
                    }
                )
            } }

            item { SegmentedColumn(title = "关于") {
                BaseWidget(title = "Github", onClick = { context.openGithub() })
                BaseWidget(title = "版本", description = BuildConfig.VERSION_NAME)
            } }

            item { Box(Modifier.padding(16.dp)) {} }
        }
    }
}
