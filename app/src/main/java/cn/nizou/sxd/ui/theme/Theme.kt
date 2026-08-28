package cn.nizou.sxd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 基于 seedColor 的 Material3 色板（对齐 WeKit 自定义取色语义）。
 * primary 用 seed 色；secondary/tertiary 用 seed 的浅/深派生，保持和谐。
 */
private fun lightColors(seed: Color) = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = seed.copy(alpha = 0.2f),
    onPrimaryContainer = seed,
    secondary = seed.copy(red = (seed.red * 0.7f + 0.3f).coerceIn(0f, 1f)),
    tertiary = seed.copy(green = (seed.green * 0.6f + 0.4f).coerceIn(0f, 1f)),
)

private fun darkColors(seed: Color) = darkColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = seed.copy(alpha = 0.3f),
    onPrimaryContainer = seed.copy(alpha = 0.9f),
    secondary = seed.copy(red = (seed.red * 0.5f + 0.5f).coerceIn(0f, 1f)),
    tertiary = seed.copy(green = (seed.green * 0.4f + 0.6f).coerceIn(0f, 1f)),
)

/**
 * Material3 主题。宿主（小猿口算）与独立模块 UI 共用同一套配色，保证注入面板与
 * 独立 MainActivity 观感一致。宿主侧不能依赖宿主自身资源，因此这里不读取自定义 R 主题。
 *
 * @param seedColor 自定义取色（默认模块绿 0xFF2E7D32）；改色即时生效（状态驱动重组）。
 */
@Composable
fun AutoOralTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Int = ThemeSettings.DEFAULT_SEED_COLOR,
    content: @Composable () -> Unit
) {
    val seed = Color(seedColor)
    val colorScheme = if (darkTheme) darkColors(seed) else lightColors(seed)
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
