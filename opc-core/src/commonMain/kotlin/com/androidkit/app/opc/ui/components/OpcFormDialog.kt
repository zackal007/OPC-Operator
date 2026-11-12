package com.androidkit.app.opc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.androidkit.app.ui.components.SimpleTextField

/** 表单里的一栏。 */
data class OpcField(
    val key: String,
    val label: String,
    val initial: String = "",
    val numeric: Boolean = false,
)

/**
 * 多栏输入弹窗 —— 加账号、记指标、加内容、新建目标都用它。
 *
 * kit 的 `InputDialog` 只有一栏；这里要一次填好几栏，所以自己写一个。
 */
@Composable
fun OpcFormDialog(
    title: String,
    fields: List<OpcField>,
    confirmText: String,
    dismissText: String,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var values by remember(fields) { mutableStateOf(fields.associate { it.key to it.initial }) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp).widthIn(max = 420.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                fields.forEach { field ->
                    SimpleTextField(
                        value = values[field.key].orEmpty(),
                        onValueChange = { values = values + (field.key to it) },
                        label = field.label,
                        keyboardOptions = if (field.numeric) {
                            KeyboardOptions(keyboardType = KeyboardType.Number)
                        } else {
                            KeyboardOptions.Default
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(dismissText) }
                    TextButton(onClick = { onConfirm(values) }) { Text(confirmText) }
                }
            }
        }
    }
}
