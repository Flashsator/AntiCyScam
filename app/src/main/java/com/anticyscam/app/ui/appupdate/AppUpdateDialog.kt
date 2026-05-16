package com.anticyscam.app.ui.appupdate

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anticyscam.app.data.appupdate.AppUpdateChecker

/**
 * Renders the APK-update prompt as a Material 3 AlertDialog. State is fully
 * driven by [AppUpdateChecker]; the parent decides when to read the StateFlow
 * and pass the snapshot in.
 *
 * Idle → nothing rendered. Sibling of `CatalogUpdateDialog`, but this one
 * updates the whole app binary, not just the scam-data file.
 */
@Composable
fun AppUpdateDialog(
    state: AppUpdateChecker.State,
    onAccept: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: (versionCode: Int) -> Unit,
    onClose: () -> Unit
) {
    when (state) {
        AppUpdateChecker.State.Idle -> Unit

        is AppUpdateChecker.State.UpdateAvailable -> AlertDialog(
            onDismissRequest = { onDismiss(state.versionCode) },
            title = { Text("App 有新版本") },
            text = {
                Text(
                    buildString {
                        append("目前版本：${state.currentVersionName}\n")
                        append("最新版本：${state.versionName}\n\n")
                        if (state.notes.isNotBlank()) {
                            append(state.notes)
                        } else {
                            append("建議更新以取得最新防詐功能與修正。")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onAccept) { Text("立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss(state.versionCode) }) { Text("暫不更新") }
            }
        )

        AppUpdateChecker.State.Downloading -> AlertDialog(
            onDismissRequest = { /* not dismissible during download */ },
            title = { Text("正在下載更新…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("請稍候，下載完成後會引導你安裝。")
                }
            },
            confirmButton = { /* none */ }
        )

        is AppUpdateChecker.State.ReadyToInstall -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("更新已下載完成") },
            text = {
                Text(
                    "版本 ${state.versionName} 已下載並通過完整性驗證。\n\n" +
                        "點「安裝」後由系統的安裝程式接手。若是首次安裝，" +
                        "Android 會先請你允許「安裝不明應用程式」，允許後再按一次安裝即可。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onInstall) { Text("安裝") }
            },
            dismissButton = {
                TextButton(onClick = onClose) { Text("稍後") }
            }
        )

        is AppUpdateChecker.State.Failed -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("更新失敗") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onClose) { Text("好") }
            }
        )

        is AppUpdateChecker.State.NoUpdate -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("目前已是最新版本") },
            text = {
                Text(
                    "App 版本：${state.currentVersionName}",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onClose) { Text("好") }
            }
        )
    }
}
