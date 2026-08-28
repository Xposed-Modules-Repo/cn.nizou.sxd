// Ported from WeKit (dev.ujhhgtg.wekit.ui.navigation.M3NavEffects) to cn.nizou.sxd.
package cn.nizou.sxd.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.ui.theme.PageTransitionAnimation
import cn.nizou.sxd.ui.theme.ThemeSettings
import cn.nizou.sxd.ui.utils.rememberDeviceCornerRadius
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects

/** Shared NavDisplay effects for the module's Material 3 settings UIs. */
@Composable
fun rememberM3NavEffects(): NavDisplayEffects {
    val cornerRadius = rememberDeviceCornerRadius(defaultRadius = 32.dp)
    val backdropColor = MaterialTheme.colorScheme.surfaceContainer
    val roundAllCorners = ThemeSettings.pageTransitionAnimation == PageTransitionAnimation.AOSP
    return remember(cornerRadius, backdropColor, roundAllCorners) {
        NavDisplayEffects(
            enableCornerClip = true,
            cornerClipRadius = cornerRadius,
            cornerClipMode = if (roundAllCorners) NavCornerClipMode.All else NavCornerClipMode.Leading,
            dimAmount = 0.5f,
            backdropColor = backdropColor,
            blockInputDuringTransition = false,
        )
    }
}
