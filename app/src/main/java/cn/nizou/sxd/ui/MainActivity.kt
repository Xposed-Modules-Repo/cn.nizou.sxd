package cn.nizou.sxd.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.nizou.sxd.ui.settings.SettingsScreen
import cn.nizou.sxd.ui.theme.AutoOralTheme
import cn.nizou.sxd.util.StringRes

/**
 * 独立模块 UI 入口（launcher / MODULE_SETTINGS）。Compose 单 Activity，
 * 直接渲染完整设置页（含通用/练习/PK/Debug/关于）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoOralTheme {
                SettingsScreen(
                    res = StringRes(resources),
                    onBack = { finish() }
                )
            }
        }
    }
}
