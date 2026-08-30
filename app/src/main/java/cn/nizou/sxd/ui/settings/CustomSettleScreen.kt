package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

/** 自定义结算时间：设置提交 costTime（**毫秒**，0=按模拟答题间隔算）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSettleScreen(res: StringRes, onBack: () -> Unit) {
    var settle by remember {
        mutableStateOf(SettingsPrefs.readString(res, "pk_settle_time", "10"))
    }
    var enabled by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_PK_SETTLE_ENABLED, false))
    }
    M3ListScaffold(title = "自定义结算时间", navigationIcon = { M3BackButton(onClick = onBack) }) {
        item {
            SegmentedColumn(title = "结算时间") {
                SwitchWidget(
                    title = "启用自定义结算时间",
                    description = "独立开关：任意 PK 模式（含停用）提交都用下方结算用时，不要求选秒结算",
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        SettingsPrefs.writeBoolean(res, res.KEY_PK_SETTLE_ENABLED, it)
                    }
                )
                TextFieldDialogWidget(
                    title = "结算用时 (毫秒)",
                    value = settle,
                    placeholder = "毫秒，如 100；0=默认（按模拟答题间隔）；最短 10 (0.01s)",
                    keyboardType = KeyboardType.Number,
                    filter = { it.filter { c -> c.isDigit() } },
                    onValueChange = {
                        settle = it
                        SettingsPrefs.writeString(res, "pk_settle_time", it)
                    }
                )
                Text(
                    text = "说明：开关开启后，提交包的 costTime 直接使用该值（毫秒），与 PK 模式选择无关。\n填 0 则按「模拟答题间隔 × 题目数」计算。\n注意：答题时间 0.00s 会触发服务端外挂检测（封禁），已强制保底最短 0.01 秒（即 10 毫秒）。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}