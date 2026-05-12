package com.anticyscam.app.ui.mainfunction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import com.anticyscam.app.ui.theme.SurfaceElevated
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.WarningRed

/**
 * Dialog for adding a new transfer account. Validation is intentionally
 * minimal here — the repository enforces non-empty fields + the 5-row cap
 * and the screen reflects any rejection via the snackbar.
 */
@Composable
fun AddTransferAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, accountNumber: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    val canSave = name.isNotBlank() && accountNumber.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text(stringResource(R.string.transfer_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
