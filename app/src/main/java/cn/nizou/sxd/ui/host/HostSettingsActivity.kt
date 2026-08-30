package cn.nizou.sxd.ui.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Keep
import cn.nizou.sxd.R
import cn.nizou.sxd.XposedInit
import cn.nizou.sxd.ui.MainPagerScreen
import cn.nizou.sxd.util.StringRes

/**
 * 宿主进程内运行的模块设置 Activity（对齐 WeKit `SettingsActivity`）。
 *
 * 本类由 [cn.nizou.sxd.util.ActivityProxy] 借壳寄生在**宿主（小猿口算）进程**：
 * 用户从宿主设置页点「老挂戏老叟设置」→ startActivity 本类 → ActivityProxy 换成宿主
 * SplashActivity 壳走系统栈 → Instrumentation.newActivity 用模块 ClassLoader 创建本实例。
 * 因此本类拥有完整 Activity 生命周期、系统转场动画与预测返回（ComponentDialog 无法提供）。
 *
 * ⚠️ 铁律：
 *  - **不能在模块独立进程启动本类**（那里没有 ActivityProxy，配置读写会退化为 root 路径，
 *    且行为与宿主进程不一致）。模块本体的入口走「打开小猿口算设置页」链路，不直接启动本类。
 *  - 类名必须含 "SettingsActivity"（ActivityProxy 据此选 SETTINGS_PROXY 壳）。
 *  - UI 复用 [MainPagerScreen]（与模块本体/旧注入面板同一套 4-tab 设置菜单）。
 */
@Keep
class HostSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 借壳后 system_server 下发的 ActivityInfo 是壳（宿主 SplashActivity）的，theme 是宿主
        // Splash 主题；这里显式覆盖为模块主题（资源已在 ActivityProxy.callActivityOnCreate 注入）。
        runCatching { setTheme(R.style.Theme_AutoOralCalculation) }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainPagerScreen(
                res = StringRes(XposedInit.moduleRes),
                onFinish = { finish() },
            )
        }
    }
}
