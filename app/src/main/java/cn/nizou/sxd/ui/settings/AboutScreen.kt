package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.BuildConfig
import cn.nizou.sxd.ui.components.BaseWidget
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.ui.components.M3ListScaffold
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.util.openGithub

/** 关于页：版本与项目信息。 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    M3ListScaffold(
        title = "关于",
        navigationIcon = { M3BackButton(onClick = onBack) }
    ) {
        item {
            SegmentedColumn(title = "关于") {
                BaseWidget(title = "Github", onClick = { context.openGithub() })
                BaseWidget(title = "版本", description = BuildConfig.VERSION_NAME)
            }
        }
        item { Box(Modifier.padding(24.dp)) {} }
    }
}
