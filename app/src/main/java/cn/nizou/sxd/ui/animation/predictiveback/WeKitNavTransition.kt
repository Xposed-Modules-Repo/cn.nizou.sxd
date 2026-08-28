// Ported from WeKit (dev.ujhhgtg.wekit.ui.animation.predictiveback.WeKitNavTransition) to cn.nizou.sxd.
package cn.nizou.sxd.ui.animation.predictiveback

import cn.nizou.sxd.ui.theme.PageTransitionAnimation
import top.yukonga.miuix.kmp.nav.transition.NavTransition

fun weKitNavTransition(animation: PageTransitionAnimation): NavTransition = when (animation) {
    PageTransitionAnimation.AOSP -> AospNavTransition
    PageTransitionAnimation.MIUIX -> top.yukonga.miuix.kmp.nav.transition.NavTransitions.MiuixDefault
}
