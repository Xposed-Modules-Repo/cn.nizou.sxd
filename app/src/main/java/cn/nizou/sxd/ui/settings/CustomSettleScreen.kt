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
import cn.nizou.sxd.ui.components.TextFieldDialogWidget
import cn.nizou.sxd.util.SettingsPrefs
import cn.nizou.sxd.util.StringRes

/** 自定义结算时间：设置 QUICK 提交 costTime（毫秒，0=按模拟答题间隔算）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSettleScreen(onBack: () -> Unit) {
    var settle by remember {
        mutableStateOf(SettingsPrefs.readString(null, "pk_settle_time", "0"))
    }
    M3ListScaffold(title = "自定义结算时间", navigationIcon = { M3BackButton(onClick = onBack) }) {
        item {
            SegmentedColumn(title = "结算时间") {
                TextFieldDialogWidget(
                    title = "结算用时 (ms)",
                    value = settle,
                    placeholder = "毫秒，0=默认（按模拟答题间隔），可填很小如10→极速结算",
                    keyboardType = KeyboardType.Number,
                    filter = { it.filter { c -> c.isDigit() } },
                    onValueChange = {
                        settle = it
                        SettingsPrefs.writeString(null, "pk_settle_time", it)
                    }
                )
                Text(
                    text = "说明：秒结算(QUICK)模式下，提交包的 costTime 直接使用该值。\n填 0 则按「模拟答题间隔 × 题目数」计算。\n极速值（如 10ms）需配合已开启的秒结算使用。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
