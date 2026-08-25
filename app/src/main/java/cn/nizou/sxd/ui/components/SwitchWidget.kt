package cn.nizou.sxd.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 布尔开关行（借鉴 WeKit SwitchWidget）。整行点击即切换。
 */
@Composable
fun SwitchWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    BaseWidget(
        modifier = modifier,
        icon = icon,
        title = title,
        description = description,
        enabled = enabled,
        onClick = { if (enabled) onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors()
            )
        }
    )
}
