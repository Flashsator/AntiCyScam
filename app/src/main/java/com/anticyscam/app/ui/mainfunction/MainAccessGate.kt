package com.anticyscam.app.ui.mainfunction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anticyscam.app.R
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.DividerGray
import com.anticyscam.app.ui.theme.SuccessGreen
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary

/**
 * 防詐器主頁權限閘門畫面。
 *
 * 「使用情況存取權」與「上層顯示」未開齊時，[MainFunctionScreen] 改渲染此
 * 畫面取代轉帳帳號功能 —— 兩項都到位才放行（不開不給用）。閘門只覆蓋 防詐器
 * 分頁內容，分頁列仍可點，使用者可切到 設定 / 詐騙專區。每列附「前往啟用」
 * 捷徑直接跳系統設定，回到 App 時 [MainGateViewModel.refresh] 重新檢查。
 */
@Composable
internal fun MainAccessGate(
    usageStatsGranted: Boolean,
    overlayGranted: Boolean,
    onOpenUsageAccess: () -> Unit,
    onOpenOverlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = AlertYellow,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = stringResource(R.string.main_gate_title),
            color = AlertYellow,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.main_gate_body),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        GatePermissionCard(
            label = stringResource(R.string.setting_feature_usage),
            description = stringResource(R.string.main_gate_usage_desc),
            granted = usageStatsGranted,
            onOpen = onOpenUsageAccess
        )
        GatePermissionCard(
            label = stringResource(R.string.setting_feature_overlay),
            description = stringResource(R.string.main_gate_overlay_desc),
            granted = overlayGranted,
            onOpen = onOpenOverlay
        )
    }
}

@Composable
private fun GatePermissionCard(
    label: String,
    description: String,
    granted: Boolean,
    onOpen: () -> Unit
) {
    val accent = if (granted) SuccessGreen else AlertYellow
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, if (granted) SuccessGreen else DividerGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (granted) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.Warning
                        },
                        contentDescription = null,
                        tint = accent
                    )
                    Text(
                        text = label,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (granted) {
                    Text(
                        text = stringResource(R.string.gate_status_on),
                        color = SuccessGreen,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    TextButton(onClick = onOpen) {
                        Text(
                            text = stringResource(R.string.gate_action_enable),
                            color = AlertYellow
                        )
                    }
                }
            }
            Text(
                text = description,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
