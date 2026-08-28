package cn.nizou.sxd.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamicColorScheme

/**
 * 取色唯一真源（完整移植 wekit `SeedResolver`）：把 [ThemeSettings] 变成具体的强调色 seed 与
 * 派生的 Material3 色板。模块本体 UI 与注入宿主面板共用同一套配色，保证两边观感一致。
 */
object SeedResolver {

    private val wallpaperSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** 平台壁纸强调色（primary），不可用（SDK < 31）时返回 null。 */
    @SuppressLint("NewApi") // gated on [wallpaperSupported]
    private fun wallpaperAccent(context: Context, dark: Boolean): Int? {
        if (!wallpaperSupported) return null
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return scheme.primary.toArgb()
    }

    /** 动态壁纸取色开启时用平台壁纸主色，否则用用户选择的 seed 色。 */
    fun customSeed(context: Context, dark: Boolean): Int =
        if (ThemeSettings.dynamicWallpaper) wallpaperAccent(context, dark) ?: ThemeSettings.seedColor
        else ThemeSettings.seedColor

    /**
     * 注入宿主面板用的 seed：与 [customSeed] 相同 —— 本项目没有 wekit 的 applyToWechat 开关，
     * 注入宿主的小猿口算面板与模块本体使用同一套配色。
     */
    fun injectedSeed(context: Context, dark: Boolean): Int = customSeed(context, dark)

    /** 由 [seed] 按当前 palette style + color spec 生成 Material3 [ColorScheme]。 */
    fun materialScheme(seed: Int, dark: Boolean): ColorScheme = dynamicColorScheme(
        seedColor = Color(seed),
        isDark = dark,
        style = ThemeSettings.paletteStyle.materialKolor,
        specVersion = ThemeSettings.effectiveColorSpec.materialKolor,
    )
}
