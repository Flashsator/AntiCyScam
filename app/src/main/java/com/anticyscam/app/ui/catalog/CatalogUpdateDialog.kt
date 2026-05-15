package com.anticyscam.app.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anticyscam.app.data.catalog.CatalogUpdateChecker

/**
 * Renders the catalog-update prompt as a Material 3 AlertDialog. State is
 * fully driven by [CatalogUpdateChecker]; the parent decides when to read
 * the StateFlow and pass the snapshot in.
 *
 * Idle → nothing rendered.
 */
@Composable
fun CatalogUpdateDialog(
    state: CatalogUpdateChecker.State,
    onAccept: () -> Unit,
    onDismiss: (version: Int) -> Unit,
    onClose: () -> Unit
) {
    when (state) {
        CatalogUpdateChecker.State.Idle -> Unit

        is CatalogUpdateChecker.State.UpdateAvailable -> AlertDialog(
            onDismissRequest = { onDismiss(state.remoteVersion) },
            title = { Text("詐騙資料庫有新版本") },
            text = {
                Text(
                    "目前版本：v${state.currentVersion}\n" +
                            "最新版本：v${state.remoteVersion}\n\n" +
                            "更新內容由 GH Actions 每週從 165 反詐騙官網彙整。" +
                            "建議更新以取得最新詐騙手法、可疑帳號與名單。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onAccept) { Text("立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss(state.remoteVersion) }) { Text("暫不更新") }
            }
        )

        CatalogUpdateChecker.State.Downloading -> AlertDialog(
            onDismissRequest = { /* not dismissible during download */ },
            title = { Text("正在下載最新資料庫…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = androidx.compose.ui.Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = androidx.compose.ui.Modifier.width(12.dp))
                    Text("請稍候，下載完成後會自動套用。")
                }
            },
            confirmButton = { /* none */ }
        )

        is CatalogUpdateChecker.State.Done -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("已更新到 v${state.version}") },
            text = { DoneSummaryBody(summary = state.summary) },
            confirmButton = {
                TextButton(onClick = onClose) { Text("好") }
            }
        )

        is CatalogUpdateChecker.State.Failed -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("更新失敗") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onClose) { Text("好") }
            }
        )

        is CatalogUpdateChecker.State.NoUpdate -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("目前已是最新版本") },
            text = {
                Text(
                    "詐騙資料庫版本：v${state.currentVersion}\n稍後我們會持續從 165 反詐騙官網更新。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onClose) { Text("好") }
            }
        )
    }
}

@Composable
private fun DoneSummaryBody(summary: CatalogUpdateChecker.UpdateSummary?) {
    // summary 為 null 走 fallback：通常是 before/after load 任一失敗，
    // 雖然極少見但仍保留通用提示，避免顯示空白 dialog。
    if (summary == null) {
        Text(
            text = "詐騙資料庫已套用最新版本。",
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    val changed = summary.sections.filter { it.isChanged }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "版本：v${summary.fromVersion} → v${summary.toVersion}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (changed.isEmpty()) {
            Text(
                text = "資料筆數無變動，僅檔案 hash 更新。",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = "更新內容：",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            changed.forEach { section ->
                Text(
                    text = formatSection(section),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatSection(section: CatalogUpdateChecker.SectionDelta): String =
    buildString {
        append("• ")
        append(section.label)
        append("：")
        when {
            section.added > 0 && section.removed > 0 ->
                append("新增 ${section.added} 筆、移除 ${section.removed} 筆")
            section.added > 0 -> append("新增 ${section.added} 筆")
            section.removed > 0 -> append("移除 ${section.removed} 筆")
        }
        append("（目前共 ${section.total} 筆）")
    }
