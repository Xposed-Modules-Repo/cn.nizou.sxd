package cn.nizou.sxd.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 单选行（借鉴 WeKit RadioButtonWidget，简化为单选驱动，无二级详情）。
 */
@Composable
fun RadioButtonWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    selected: Boolean,
    onSelect: () -> Unit
) {
    BaseWidget(
        modifier = modifier,
        icon = icon,
        title = title,
        description = description,
        enabled = enabled,
        selected = selected,
        onClick = { if (enabled) onSelect() },
        trailingContent = {
            RadioButton(
                selected = selected,
                enabled = enabled,
                onClick = { if (enabled) onSelect() }
            )
        }
    )
}
