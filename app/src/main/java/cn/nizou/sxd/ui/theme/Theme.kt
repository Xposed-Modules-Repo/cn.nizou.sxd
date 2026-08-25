package cn.nizou.sxd.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * Material3 主题。宿主（小猿口算）与独立模块 UI 共用同一套配色，保证注入面板与
 * 独立 MainActivity 观感一致。宿主侧不能依赖宿主自身资源，因此这里不读取自定义 R 主题。
 */
@Composable
fun AutoOralTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ 动态取色（宿主是 3.94 版本，minSdk 27，动态取色仅在高版本生效）
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
