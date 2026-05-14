package com.anticyscam.app.ui.mainfunction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anticyscam.app.R
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SurfaceElevated
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.WarningRed

/**
 * Dialog for adding OR editing a transfer account. Validation is intentionally
 * minimal here — the repository enforces non-empty fields, the 5-row cap, the
 * one-shot edit slot, and any rejection surfaces via the snackbar.
 *
 * Add mode: empty fields, yellow warning at 2 adds today.
 * Edit mode: fields pre-populated from the existing row; an inline notice
 *   reminds the user this is the only edit they get.
 */
@Composable
fun AddTransferAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, accountNumber: String) -> Unit,
    showSecondAddWarning: Boolean = false,
    isEditMode: Boolean = false,
    initialName: String = "",
    initialAccountNumber: String = ""
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var accountNumber by remember(initialAccountNumber) { mutableStateOf(initialAccountNumber) }
    val canSave = name.isNotBlank() && accountNumber.isNotBlank()
    val titleRes = if (isEditMode) R.string.transfer_edit_title else R.string.transfer_add_title

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isEditMode) {
                    Text(
                        text = stringResource(R.string.transfer_edit_warning),
                        color = AlertYellow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (showSecondAddWarning) {
                    Text(
                        text = stringResource(R.string.daily_add_lock_warn_2),
                        color = AlertYellow,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.transfer_field_name)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.transfer_field_account)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), accountNumber.trim()) },
                enabled = canSave
            ) {
                Text(
                    text = stringResource(R.string.transfer_action_save),
                    color = if (canSave) WarningRed else TextPrimary.copy(alpha = 0.4f)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.transfer_action_cancel), color = TextPrimary)
            }
        }
    )
}
