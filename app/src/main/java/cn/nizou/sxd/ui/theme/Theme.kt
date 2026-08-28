package cn.nizou.sxd.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 模块 Material3 主题 —— 完整移植 wekit `ModuleTheme` 语义，由 [ThemeSettings] 驱动：
 *
 *  - 色板用 [SeedResolver.materialScheme] 按所选 seed + palette style + color spec 生成
 *    （material-kolor 动态配色，与 wekit 一模一样）；
 *  - 深色与否由 [AppThemeMode.resolve] 决定（跟随系统 / 浅色 / 深色）；
 *  - 动态壁纸取色开启时，seed 改用平台壁纸主色（[SeedResolver.customSeed]）。
 *
 * 每个 [ThemeSettings] 值都是可观察状态，设置行改动即时重组换肤。
 * 宿主（小猿口算）注入面板与独立模块 UI 共用本主题，保证观感一致。
 *
 * @param darkTheme 深色与否（默认 [ThemeSettings.themeMode] 解析结果）。
 * @param seedColor 自定义取色 seed（默认 [ThemeSettings.seedColor]；动态壁纸取色开启时
 *                  以壁纸主色为准，本参数被忽略）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AutoOralTheme(
    darkTheme: Boolean = ThemeSettings.themeMode.resolve(),
    seedColor: Int = ThemeSettings.seedColor,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val seed = if (ThemeSettings.dynamicWallpaper) {
        SeedResolver.customSeed(context, darkTheme)
    } else {
        seedColor
    }
    MaterialExpressiveTheme(
        colorScheme = SeedResolver.materialScheme(seed, darkTheme),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/**
 * wekit 风格入口（别名，与 wekit `ModuleTheme` 同名同构）：模块本体 / 注入面板统一走
 * [AutoOralTheme]（wekit 的 ModuleTheme 即「由 ThemeSettings 驱动的模块 UI 主题」）。
 */
@Composable
fun ModuleTheme(
    darkTheme: Boolean = ThemeSettings.themeMode.resolve(),
    content: @Composable () -> Unit
) {
    AutoOralTheme(darkTheme = darkTheme, content = content)
}
