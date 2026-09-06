package cn.nizou.sxd.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down

/** 下拉选项（照抄 WeKit DropdownOption）。 */
data class DropdownOption<T>(val value: T, val label: String)

/**
 * Material3 下拉选择行。
 *
 * Popup 必须是 [BaseWidget] 的同级节点：BaseWidget 的 trailing 槽会走 intrinsic
 * measurement，而 DropdownMenuPopup 内部基于 SubcomposeLayout；把 Popup 放在槽里会在
 * 展开时抛出 “Asking for intrinsic measurements of SubcomposeLayout” 并杀掉宿主进程。
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
    val selected = options.firstOrNull { it.value == value }

    // The Box is the popup anchor. Its child BaseWidget remains intrinsic-safe.
    Box(Modifier.fillMaxWidth()) {
        BaseWidget(
            icon = icon,
            title = title,
            description = description ?: selected?.label ?: "未选择",
            enabled = enabled,
            onClick = if (enabled) ({ expanded = !expanded }) else null,
            trailingContent = {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Arrow_drop_down,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // One bounded scroll container: no nested lazy/subcompose layout.
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
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
    }
}
