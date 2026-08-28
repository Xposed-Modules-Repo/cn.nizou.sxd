package cn.nizou.sxd.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.nizou.sxd.ui.theme.AutoOralTheme
import cn.nizou.sxd.util.StringRes
import cn.nizou.sxd.util.crash.JavaCrashHandler

/**
 * 独立模块 UI 入口（launcher / MODULE_SETTINGS）。Compose 单 Activity。
 *
 * 模块本体（独立 App）：`ModuleMainScreen` 为「简单启动器页」（激活卡片 + 打开宿主 +
 * 打开模块设置 + GitHub 链接），不含模块内部功能。模块全部功能仅保留在注入宿主面板
 * （cn.nizou.sxd.ui.host.SettingsPanel / MainPagerScreen）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 模块独立进程崩溃捕获（宿主注入进程由 XposedInit.onModuleLoaded 安装；
        // 本进程不触发 onModuleLoaded，独立打开时需在此补装；失败不影响启动）
        runCatching { JavaCrashHandler.install() }
        setContent {
            AutoOralTheme {
                ModuleMainScreen(
                    res = StringRes(resources),
                    onFinish = { finish() }
                )
            }
        }
    }
}
