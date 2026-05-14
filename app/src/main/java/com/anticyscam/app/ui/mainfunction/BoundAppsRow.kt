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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anticyscam.app.domain.model.BoundApp
import com.anticyscam.app.ui.components.AppIcon
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.WarningRed

/**
 * 水平捲動的已綁定 App 圖示列。點擊 = Phase 6 將觸發「授權啟動」流程。
 */
@Composable
fun BoundAppsRow(
    apps: List<BoundApp>,
    onAppClick: (BoundApp) -> Unit,
    modifier: Modifier = Modifier
) {
    if (apps.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp),
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
        items(items = apps, key = { it.packageName }) { app ->
            BoundAppTile(app = app, onClick = { onAppClick(app) })
        }
    }
}

@Composable
private fun BoundAppTile(app: BoundApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(width = 92.dp, height = 92.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(width = 1.dp, color = WarningRed),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppIcon(
                packageName = app.packageName,
                modifier = Modifier.size(36.dp),
                fallbackTint = WarningRed
            )
            Text(
                text = app.label,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
