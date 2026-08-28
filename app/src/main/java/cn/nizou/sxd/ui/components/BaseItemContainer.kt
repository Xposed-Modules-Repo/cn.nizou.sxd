package cn.nizou.sxd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/**
 * SegmentedColumn 内 item 的动态圆角（WeKit 同款 CompositionLocal，默认 [CornerRadius]）。
 * [BaseItemContainer] 等组内容器读取它做圆角裁剪。
 */
val LocalSegmentedItemShape: CompositionLocal<Shape> =
    compositionLocalOf { RoundedCornerShape(CornerRadius) }

/**
 * 分组内 item 容器（照抄 WeKit `BaseItemContainer`）：按 [LocalSegmentedItemShape] 圆角
 * 裁剪 + 背景色包裹内容。背景用与项目 [SegmentedColumn]/[BaseWidget] 一致的
 * `surfaceContainerHigh`（wekit 原为 surfaceBright，适配本项目设计语言）。
 */
@Composable
fun BaseItemContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val baseShape = LocalSegmentedItemShape.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(baseShape)
            .background(backgroundColor),
    ) {
        content()
    }
}
