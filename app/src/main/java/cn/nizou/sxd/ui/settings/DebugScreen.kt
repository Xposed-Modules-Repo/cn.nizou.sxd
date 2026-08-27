package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.ui.components.M3ListScaffold
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.ui.components.SwitchWidget
import cn.nizou.sxd.util.SettingsPrefs
import cn.nizou.sxd.util.StringRes

/** Debug 设置页。 */
@Composable
fun DebugScreen(res: StringRes, onBack: () -> Unit) {
    var debug by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_DEBUG, false))
    }

    M3ListScaffold(
        title = "Debug",
        navigationIcon = { M3BackButton(onClick = onBack) }
    ) {
        item {
            SegmentedColumn(title = "Debug") {
                SwitchWidget(
                    title = "DEBUG",
                    description = "没事别开",
                    checked = debug,
                    onCheckedChange = {
                        debug = it
                        SettingsPrefs.writeBoolean(res, res.KEY_DEBUG, it)
                    }
                )
            }
        }
        item { Box(Modifier.padding(24.dp)) {} }
    }
}
