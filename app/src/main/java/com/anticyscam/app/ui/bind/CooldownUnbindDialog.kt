package com.anticyscam.app.ui.bind

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed

/**
 * Bulk confirmation for "uncheck a row that has already matured (>= 24h)".
 *
 * Lists every package the user is about to unbind, explains that this kicks
 * off a 48-hour delayed unbind (the row stays bound during the cooldown),
 * and that the user can cancel from the binding screen later.
 *
 * The red primary button is deliberately heavy — the user is choosing to
 * step away from protection, and the visual weight should match the intent.
 */
@Composable
fun CooldownUnbindDialog(
    appLabels: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBlack,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "啟動 48 小時冷靜解除？",
                style = MaterialTheme.typography.titleMedium,
                color = AlertYellow
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "以下 ${appLabels.size} 個 App 已綁定超過 24 小時，必須通過 48 小時冷靜期才會自動解除。冷靜期間它們仍受防詐器保護。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = appLabels.joinToString("、"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "若中途反悔，可隨時在綁定畫面取消冷靜，但已倒數的時間會歸零。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningRed,
                    contentColor = TextPrimary
                )
            ) {
                Text("啟動 48 小時冷靜期")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("再想想", color = TextSecondary)
            }
        }
    )
}
