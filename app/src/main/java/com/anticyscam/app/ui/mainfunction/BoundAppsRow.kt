package com.anticyscam.app.ui.mainfunction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anticyscam.app.domain.model.BindingState
import com.anticyscam.app.domain.model.BoundApp
import com.anticyscam.app.domain.model.BoundAppWithState
import com.anticyscam.app.ui.components.AppIcon
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed

/**
 * 水平捲動的已綁定 App 圖示列。每個 tile 底部會根據 [BindingState] 顯示：
 *   - PendingMaturation → 黃色「綁定中 hh:mm:ss」倒數
 *   - Matured            → 灰色「已綁定」
 *   - PendingUnbind      → 紅色「解除中 剩 hh:mm:ss」倒數
 *   - Unbound            → 不顯示 footer（理論上不會出現在這個列表）
 */
@Composable
fun BoundAppsRow(
    apps: List<BoundAppWithState>,
    onAppClick: (BoundApp) -> Unit,
    modifier: Modifier = Modifier
) {
    if (apps.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "尚未綁定任何 App — 請先點擊上方按鈕綁定",
                color = TextPrimary.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = apps, key = { it.app.packageName }) { entry ->
            BoundAppTile(entry = entry, onClick = { onAppClick(entry.app) })
        }
    }
}

@Composable
private fun BoundAppTile(entry: BoundAppWithState, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(width = 84.dp, height = 104.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(width = 1.dp, color = WarningRed),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AppIcon(
                packageName = entry.app.packageName,
                modifier = Modifier.size(28.dp),
                fallbackTint = WarningRed
            )
            Text(
                text = entry.app.label,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            StateFooter(state = entry.state)
        }
    }
}

@Composable
private fun StateFooter(state: BindingState) {
    when (state) {
        is BindingState.PendingMaturation -> Text(
            text = "綁定中\n${formatHms(state.remainingMs)}",
            color = AlertYellow,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
        BindingState.Matured -> Text(
            text = "已綁定",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        is BindingState.PendingUnbind -> Text(
            text = "解除中\n${formatHms(state.remainingMs)}",
            color = WarningRed,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 2,
            textAlign = TextAlign.Center
        )
        BindingState.Unbound -> Unit
    }
}

private fun formatHms(remainingMs: Long): String {
    val total = (remainingMs / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
