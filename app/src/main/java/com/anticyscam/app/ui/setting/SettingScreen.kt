package com.anticyscam.app.ui.setting

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.anticyscam.app.BuildConfig
import com.anticyscam.app.R
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.DividerGray
import com.anticyscam.app.ui.theme.SuccessGreen
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.utils.AccessibilityChecker
import kotlinx.coroutines.launch

/**
 * 設定頁 — 顯示反詐器目前狀態與資料管理。
 *
 * 區塊：
 *  1. 無障礙服務狀態（onResume 重新檢查；未啟用時提供前往系統設定的按鈕）
 *  2. 統計：已綁定 App 數 / 已建立轉帳帳號數
 *  3. 165 反詐騙專線（撥號 Intent）
 *  4. 危險操作：清除所有資料（含確認對話框）
 *  5. 關於：版本資訊
 */
@Composable
fun SettingScreen() {
    val viewModel: SettingViewModel = hiltViewModel()
    val status by viewModel.status.collectAsState()
    val pendingClear by viewModel.pendingClear.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val clearedMsg = "已清除所有資料"

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccessibilityStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = SurfaceBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.nav_setting),
                    color = WarningRed,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            item {
                AccessibilityStatusCard(
                    enabled = status.accessibilityEnabled,
                    onOpenSettings = {
                        context.startActivity(AccessibilityChecker.openSettingsIntent())
                    }
                )
            }
            item {
                StatsCard(
                    boundAppCount = status.boundAppCount,
                    transferAccountCount = status.transferAccountCount
                )
            }
            item {
                HotlineCard(onDial = { dial165(context) })
            }
            item {
                DangerZoneCard(onClick = viewModel::requestClearAll)
            }
            item {
                AboutCard()
            }
        }
    }

    if (pendingClear) {
        ClearConfirmDialog(
            onCancel = viewModel::cancelClearAll,
            onConfirm = {
                viewModel.confirmClearAll {
                    scope.launch { snackbarHostState.showSnackbar(clearedMsg) }
                }
            }
        )
    }
}

@Composable
private fun AccessibilityStatusCard(enabled: Boolean, onOpenSettings: () -> Unit) {
    val borderColor = if (enabled) SuccessGreen else WarningRed
    val icon = if (enabled) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val statusText = if (enabled) {
        stringResource(R.string.gate_status_enabled)
    } else {
        stringResource(R.string.gate_status_disabled)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = borderColor)
                Text(
                    text = "無障礙服務",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "目前狀態：$statusText",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!enabled) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, WarningRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed)
                ) {
                    Text(text = stringResource(R.string.gate_open_settings))
                }
            }
        }
    }
}

@Composable
private fun StatsCard(boundAppCount: Int, transferAccountCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, DividerGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "目前資料",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            StatsRow(label = "已綁定 App", value = "$boundAppCount")
            StatsRow(
                label = "已建立轉帳帳號（不含臨時用）",
                value = "$transferAccountCount / 5"
            )
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HotlineCard(onDial: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, AlertYellow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    tint = AlertYellow
                )
                Text(
                    text = "165 反詐騙專線",
                    color = AlertYellow,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "24 小時諮詢／報案。遇到可疑來電或可疑帳號可立即查詢。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = onDial,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AlertYellow),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertYellow)
            ) {
                Text(text = "撥打 165")
            }
        }
    }
}

@Composable
private fun DangerZoneCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, WarningRed)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = WarningRed
                )
                Text(
                    text = "危險區",
                    color = WarningRed,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "清除所有資料：移除所有已綁定 App 與所有新增的轉帳帳號（保留預設「臨時用」）。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningRed,
                    contentColor = TextPrimary
                )
            ) {
                Text(text = "清除所有資料")
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, DividerGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "關於",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            StatsRow(label = "App 名稱", value = stringResource(R.string.app_name))
            StatsRow(label = "版本", value = BuildConfig.VERSION_NAME)
        }
    }
}

@Composable
private fun ClearConfirmDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = SurfaceDim,
        title = {
            Text(text = "確認清除所有資料？", color = WarningRed)
        },
        text = {
            Text(
                text = "此操作無法復原。所有已綁定的 App 與所有自訂轉帳帳號將被刪除。",
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "確認清除", color = WarningRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.transfer_action_cancel), color = TextPrimary)
            }
        }
    )
}

private fun dial165(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:165")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
