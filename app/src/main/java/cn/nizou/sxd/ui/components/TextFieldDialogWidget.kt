package cn.nizou.sxd.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Chevron_right

/**
 * 文本/数字输入行（借鉴 WeKit TextFieldDialogWidget）。点击行弹出输入对话框，
 * 确认后回调。用于「循环时间间隔」「自定义答题 JS」「极速模式间隔」等。
 *
 * @param value 当前值（显示在行 description）
 * @param onValueChange 对话框确认后回调
 * @param placeholder value 为空时行上显示的占位提示
 * @param keyboardType 键盘类型（Text / Number）
 * @param singleLine 是否单行
 */
@Composable
fun TextFieldDialogWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    dialogTitle: String = title,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
    enabled: Boolean = true,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    filter: (String) -> String = { it }
) {
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }

    BaseWidget(
        modifier = modifier,
        icon = icon,
        title = title,
        description = value.ifBlank { placeholder },
        enabled = enabled,
        onClick = {
            if (enabled) {
                draft = value
                showDialog = true
            }
        },
        trailingContent = {
            Icon(
                imageVector = MaterialSymbols.Outlined.Chevron_right,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(dialogTitle) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = filter(it) },
                    singleLine = singleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onValueChange(draft)
                }) { Text(confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(dismissLabel) }
            }
        )
    }
}
