package cn.nizou.sxd.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down

/** 下拉选项（照抄 WeKit DropdownOption）。 */
data class DropdownOption<T>(val value: T, val label: String)

/**
 * 下拉选择行（照抄 WeKit `DropDownMenuWidget`）。
 *
 * 点击整行弹出 [DropdownMenuPopup]，选项带选中态；description 为空时显示当前选中项。
 * 与 WeKit 的唯一差异：本项目 BaseWidget 没有 `foreContent` 槽，锚点放在尾部
 * `Arrow_drop_down` 箭头处（[DropdownMenuPopup] 相对该 Box 定位）。
 *
 * @param icon 可选前置图标
 * @param title 主标题
 * @param description 支持文本；null 时显示当前选中项 label
 * @param value 当前值（须命中 [options] 中某项）
 * @param options 候选项列表
 * @param enabled 是否可交互
 * @param onValueChange 选择回调（选中即生效）
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> DropDownMenuWidget(
    icon: ImageVector? = null,
    title: String,
    description: String?,
    value: T,
    options: List<DropdownOption<T>>,
    enabled: Boolean = true,
    onValueChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.first { it.value == value }

    BaseWidget(
        icon = icon,
        title = title,
        description = description ?: selected.label,
        enabled = enabled,
        onClick = if (enabled) ({ expanded = !expanded }) else null,
        trailingContent = {
            Box {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Arrow_drop_down,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                DropdownMenuPopup(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                        options.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                selected = option.value == value,
                                onClick = {
                                    onValueChange(option.value)
                                    expanded = false
                                },
                                text = { Text(option.label) },
                                shapes = MenuDefaults.itemShape(index, options.size),
                            )
                        }
                    }
                }
            }
        },
    )
}
