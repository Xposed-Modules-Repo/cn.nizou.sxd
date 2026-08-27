package cn.nizou.sxd.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.nizou.sxd.ui.theme.AutoOralTheme
import cn.nizou.sxd.util.StringRes

/**
 * 独立模块 UI 入口（launcher / MODULE_SETTINGS）。Compose 单 Activity。
 *
 * 模块本体（独立 App）：`ModuleMainScreen` 用 `HorizontalPager` + `FloatingBottomBar`
 * 悬浮胶囊底栏切换分类，首页为「功能菜单列表」（激活检测卡片 + GitHub + 分类下钻），
 * 与注入宿主面板（cn.nizou.sxd.ui.host.SettingsPanel）分离，复用同一批详情 Composable。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
