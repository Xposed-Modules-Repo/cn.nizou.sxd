package cn.nizou.sxd.ui.theme

import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cn.nizou.sxd.util.SettingsPrefs

/**
 * 模块主题设置（对齐 WeKit `ThemeSettings` 的核心：seedColor 自定义取色）。
 *
 * seedColor 持久化到模块 prefs（键 `theme_seed_color`，默认模块绿 0xFF2E7D32），
 * [AutoOralTheme] 据此生成 Material3 色板；修改即时生效（Compose 状态驱动重组）。
 */
object ThemeSettings {

    private const val KEY_SEED_COLOR = "theme_seed_color"
    const val DEFAULT_SEED_COLOR: Int = 0xFF2E7D32.toInt()

    var seedColor by mutableIntStateOf(
        SettingsPrefs.readInt(KEY_SEED_COLOR, DEFAULT_SEED_COLOR)
    )
        private set

    fun updateSeedColor(value: Int) {
        seedColor = value
        SettingsPrefs.writeInt(KEY_SEED_COLOR, value)
    }

    /** ARGB int → HSV float[3]。 */
    fun colorToHsv(color: Int): FloatArray = FloatArray(3).also {
        Color.colorToHSV(color, it)
    }

    /** HSV(h,s,v) → ARGB int。 */
    fun hsvToColor(h: Float, s: Float, v: Float): Int =
        Color.HSVToColor(floatArrayOf(h, s, v))

    /** 取色行显示的色值字符串（如 #2E7D32）。 */
    fun seedColorHex(): String = String.format("#%06X", 0xFFFFFF and seedColor)
}
