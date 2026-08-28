package cn.nizou.sxd.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.ui.components.M3ListScaffold
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.ui.components.SwitchWidget
import cn.nizou.sxd.ui.components.TextFieldDialogWidget
import cn.nizou.sxd.util.LogOverlayWindow
import cn.nizou.sxd.util.SettingsPrefs
import cn.nizou.sxd.util.StringRes

/** Debug 设置页（含抓包 / 改包调试项）。 */
@Composable
fun DebugScreen(res: StringRes, onBack: () -> Unit) {
    var debug by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_DEBUG, false))
    }
    val context = LocalContext.current
    val overlayHost = LogOverlayWindow.isHostProcess(context)
    // 以持久化状态为初值，保证模块本体/宿主面板都能读到真实开关状态
    var logOverlay by remember {
        mutableStateOf(LogOverlayWindow.isEnabled(context) && overlayHost)
    }
    var packetCapture by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_PACKET_CAPTURE, false))
    }
    var packetRewrite by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_PACKET_REWRITE, false))
    }
    var packetWriteFile by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_PACKET_WRITE_FILE, false))
    }
    var rewriteRules by remember {
        mutableStateOf(SettingsPrefs.readString(res, res.KEY_REWRITE_RULES, "[]"))
    }
    var blockRisk by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_BLOCK_RISK_DETECT, false))
    }
    var blockSupervision by remember {
        mutableStateOf(SettingsPrefs.readBoolean(res, res.KEY_BLOCK_SUPERVISION, false))
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
                SwitchWidget(
                    title = "实时日志悬浮窗",
                    description = if (overlayHost)
                        "免悬浮窗权限，仅小猿口算内生效，可拖动、可最小化为悬浮球"
                    else
                        "仅在小猿口算（宿主）内生效，模块本体不显示",
                    checked = logOverlay,
                    onCheckedChange = {
                        val applied = LogOverlayWindow.setEnabled(context, it)
                        if (it && !applied) {
                            Toast.makeText(
                                context,
                                if (overlayHost) "悬浮日志启动失败（未找到宿主 Activity）" else "悬浮日志仅在小猿口算内生效",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        logOverlay = applied && it
                    }
                )
            }
        }
        item {
            SegmentedColumn(title = "抓包 / 改包") {
                SwitchWidget(
                    title = "抓包",
                    description = "把经过该 Retrofit 的请求/响应写入实时日志悬浮窗",
                    checked = packetCapture,
                    onCheckedChange = {
                        packetCapture = it
                        SettingsPrefs.writeBoolean(res, res.KEY_PACKET_CAPTURE, it)
                    }
                )
                SwitchWidget(
                    title = "抓包写文件",
                    description = "同时追加写 externalCacheDir/packet_capture.log",
                    checked = packetWriteFile,
                    onCheckedChange = {
                        packetWriteFile = it
                        SettingsPrefs.writeBoolean(res, res.KEY_PACKET_WRITE_FILE, it)
                    }
                )
                SwitchWidget(
                    title = "改包",
                    description = "按下方规则改请求，失败安全回落放行原请求",
                    checked = packetRewrite,
                    onCheckedChange = {
                        packetRewrite = it
                        SettingsPrefs.writeBoolean(res, res.KEY_PACKET_REWRITE, it)
                    }
                )
                TextFieldDialogWidget(
                    title = "改包规则 (JSON)",
                    value = rewriteRules,
                    placeholder = "[{\"path\":\"/leo-game-pk/android/math/pk/match\",\"method\":\"POST\",\"query\":{\"pointId\":\"3\"}}]",
                    dialogTitle = "改包规则 JSON",
                    singleLine = false,
                    onValueChange = {
                        rewriteRules = it
                        SettingsPrefs.writeString(res, res.KEY_REWRITE_RULES, it)
                    }
                )
            }
        }
        item {
            SegmentedColumn(title = "风险屏蔽") {
                SwitchWidget(
                    title = "屏蔽外挂/大朋友检测弹窗",
                    description = "开启后按内容关键字自动关闭外挂/风控/家长监督弹窗",
                    checked = blockRisk,
                    onCheckedChange = {
                        blockRisk = it
                        SettingsPrefs.writeBoolean(res, res.KEY_BLOCK_RISK_DETECT, it)
                    }
                )
                SwitchWidget(
                    title = "隐藏家长监督视图",
                    description = "强制隐藏宿主 SupervisionHelper 家长监督指示",
                    checked = blockSupervision,
                    onCheckedChange = {
                        blockSupervision = it
                        SettingsPrefs.writeBoolean(res, res.KEY_BLOCK_SUPERVISION, it)
                    }
                )
            }
        }
        item { Box(Modifier.padding(24.dp)) {} }
    }
}
