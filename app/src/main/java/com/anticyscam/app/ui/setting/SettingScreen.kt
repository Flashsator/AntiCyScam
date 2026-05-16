package com.anticyscam.app.ui.setting

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.anticyscam.app.BuildConfig
import com.anticyscam.app.R
import com.anticyscam.app.service.ForegroundAppGuard
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.DividerGray
import com.anticyscam.app.ui.theme.SuccessGreen
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// File-level allocations reused by every Card in this screen — kept out of
// the Composables so LazyColumn item composition doesn't repeatedly build
// identical Shape/BorderStroke instances. Mirrors the scroll-perf pattern
// applied to TransferAccountCard.
private val CardShape = RoundedCornerShape(12.dp)
private val DividerCardBorder = BorderStroke(1.dp, DividerGray)
private val AlertCardBorder = BorderStroke(1.dp, AlertYellow)
private val ButtonShape = RoundedCornerShape(8.dp)

/**
 * 設定頁 — 顯示防詐器目前狀態與資料管理。
 *
 * 區塊：
 *  1. 無障礙服務狀態（onResume 重新檢查；未啟用時提供前往系統設定的按鈕）
 *  2. 統計：已綁定 App 數 / 已建立轉帳帳號數
 *  3. 165 反詐騙專線（撥號 Intent）
 *  4. 關於：版本資訊
 */
@Composable
fun SettingScreen() {
    val viewModel: SettingViewModel = hiltViewModel()
    val status by viewModel.status.collectAsState()
    val diagnostic by viewModel.diagnostic.collectAsState()
    val serviceAlive by viewModel.accessibilityServiceAlive.collectAsState()
    val catalogMeta by viewModel.catalogMeta.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                ProtectionStatusCard(
                    a11yEnabled = status.accessibilityEnabled,
                    deviceAdminActive = status.deviceAdminActive,
                    notificationsEnabled = status.notificationsEnabled,
                    batteryIgnored = status.batteryOptimizationIgnored,
                    onOpenA11y = viewModel::openAccessibilitySettings,
                    onOpenDeviceAdmin = viewModel::openDeviceAdminSettings,
                    onOpenNotifications = viewModel::openNotificationSettings,
                    onOpenBattery = viewModel::requestBatteryExemption
                )
            }
            item {
                DiagnosticCard(
                    serviceAlive = serviceAlive,
                    overlayGranted = status.overlayPermissionGranted,
                    diagnostic = diagnostic,
                    onRequestOverlay = viewModel::requestOverlayPermission,
                    onFireTestWarning = viewModel::fireTestWarning
                )
            }
            item {
                StatsCard(
                    boundAppCount = status.boundAppCount,
                    transferAccountCount = status.transferAccountCount
                )
            }
            item {
                DataSourceCard(
                    meta = catalogMeta,
                    onOpenSource = { url -> openExternalUrl(context, url) }
                )
            }
            item {
                HotlineCard(onDial = { dial165(context) })
            }
            item {
                FeedbackCard(onClick = { /* TODO: 接意見回饋送出邏輯 */ })
            }
            if (BuildConfig.DEBUG) {
                item {
                    DebugZoneCard(
                        onResetCatalog = {
                            viewModel.resetCatalogUpdateStateForDebug()
                            scope.launch {
                                snackbarHostState.showSnackbar("已重設更新檢查狀態，正在重新檢查…")
                            }
                        }
                    )
                }
            }
            item {
                AboutCard(meta = catalogMeta)
            }
        }
    }
}

@Composable
private fun ProtectionStatusCard(
    a11yEnabled: Boolean,
    deviceAdminActive: Boolean,
    notificationsEnabled: Boolean,
    batteryIgnored: Boolean,
    onOpenA11y: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBattery: () -> Unit
) {
    val allOn = a11yEnabled && deviceAdminActive && notificationsEnabled && batteryIgnored
    val borderColor = if (allOn) SuccessGreen else AlertYellow
    val border = remember(borderColor) { BorderStroke(1.dp, borderColor) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = border
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
                    imageVector = if (allOn) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = borderColor
                )
                Text(
                    text = "防詐器保護狀態",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = if (allOn) {
                    "四項保護全部已啟用，防詐器正在守護您的轉帳安全。"
                } else {
                    "下方未啟用的項目，請點「前往啟用」開啟。"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            ProtectionFeatureRow(
                label = stringResource(R.string.gate_item_a11y),
                enabled = a11yEnabled,
                onOpen = onOpenA11y
            )
            ProtectionFeatureRow(
                label = stringResource(R.string.gate_item_admin),
                enabled = deviceAdminActive,
                onOpen = onOpenDeviceAdmin
            )
            ProtectionFeatureRow(
                label = stringResource(R.string.gate_item_notify),
                enabled = notificationsEnabled,
                onOpen = onOpenNotifications
            )
            ProtectionFeatureRow(
                label = stringResource(R.string.gate_item_battery),
                enabled = batteryIgnored,
                onOpen = onOpenBattery
            )
        }
    }
}

@Composable
private fun ProtectionFeatureRow(
    label: String,
    enabled: Boolean,
    onOpen: () -> Unit
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
                imageVector = if (enabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (enabled) SuccessGreen else WarningRed
            )
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (enabled) {
            Text(
                text = stringResource(R.string.gate_status_on),
                color = SuccessGreen,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            TextButton(onClick = onOpen) {
                Text(
                    text = stringResource(R.string.gate_action_enable),
                    color = WarningRed
                )
            }
        }
    }
}

@Composable
private fun StatsCard(boundAppCount: Int, transferAccountCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = DividerCardBorder
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
private fun DataSourceCard(
    meta: SettingViewModel.CatalogMeta,
    onOpenSource: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = DividerCardBorder
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
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = AlertYellow
                )
                Text(
                    text = "防詐資料來源",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (meta.version > 0 || meta.lastUpdated.isNotEmpty()) {
                val versionPart = if (meta.version > 0) "資料版本 ${meta.displayVersion}" else ""
                val datePart = if (meta.lastUpdated.isNotEmpty()) "更新日 ${meta.lastUpdated}" else ""
                Text(
                    text = listOf(versionPart, datePart).filter { it.isNotEmpty() }.joinToString("　·　"),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (meta.sources.isEmpty()) {
                Text(
                    text = "資料整理自台灣官方反詐騙公開資訊。",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "本 App 內所有詐騙手法、警示帳戶與別名清單，皆整理自以下官方來源（點擊開啟官網）：",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                meta.sources.forEach { src ->
                    OfficialSourceRow(source = src, onClick = { onOpenSource(src.url) })
                }
            }
            Text(
                text = "本 App 非政府機關，亦無與上述單位合作；資料僅供衛教參考，個案請以 165 公告與警方查證為準。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun OfficialSourceRow(
    source: SettingViewModel.OfficialSource,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = AlertYellow,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.label,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = source.url,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HotlineCard(onDial: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = AlertCardBorder
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
                shape = ButtonShape,
                border = AlertCardBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertYellow)
            ) {
                Text(text = "撥打 165")
            }
        }
    }
}

@Composable
private fun FeedbackCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = AlertCardBorder
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
                    imageVector = Icons.Filled.Feedback,
                    contentDescription = null,
                    tint = AlertYellow
                )
                Text(
                    text = "意見回饋",
                    color = AlertYellow,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "回報誤判、想法或遇到的問題，幫助我們改善這個 App。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = ButtonShape,
                border = AlertCardBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertYellow)
            ) {
                Text(text = "提供意見")
            }
        }
    }
}

/**
 * Debug-only card. Lets QA wipe the catalog-update DataStore + downloaded
 * override so the next check behaves like a fresh install — eliminates the
 * need to uninstall the APK or wait 24h to re-trigger the prompt.
 */
@Composable
private fun DebugZoneCard(onResetCatalog: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = AlertCardBorder
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
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = AlertYellow
                )
                Text(
                    text = "Debug 工具",
                    color = AlertYellow,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "重設更新檢查狀態：清除 24h 防抖、已忽略版本、已套用版本與下載的覆蓋檔，立即重新檢查線上目錄版本。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = onResetCatalog,
                modifier = Modifier.fillMaxWidth(),
                shape = ButtonShape,
                border = AlertCardBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertYellow)
            ) {
                Text(text = "重設更新檢查狀態")
            }
        }
    }
}

@Composable
private fun AboutCard(meta: SettingViewModel.CatalogMeta) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = DividerCardBorder
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
            StatsRow(label = "App 版本", value = BuildConfig.VERSION_NAME)
            if (meta.displayVersion.isNotEmpty()) {
                StatsRow(label = "詐騙資料庫版本", value = meta.displayVersion)
            }
            if (meta.lastUpdated.isNotEmpty()) {
                StatsRow(label = "資料更新日", value = meta.lastUpdated)
            }
            StatsRow(label = "資料處理方式", value = "全程裝置端離線")
        }
    }
}

@Composable
private fun DiagnosticCard(
    serviceAlive: Boolean,
    overlayGranted: Boolean,
    diagnostic: ForegroundAppGuard.Diagnostic,
    onRequestOverlay: () -> Unit,
    onFireTestWarning: () -> Unit
) {
    val allHealthy = serviceAlive && overlayGranted
    val borderColor = if (allHealthy) SuccessGreen else AlertYellow
    val border = remember(borderColor) { BorderStroke(1.dp, borderColor) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = border
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "診斷資訊",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "若綁定 App 直開時沒跳警告，請依照以下狀態逐項排除：",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            StatusRow(
                label = "服務心跳",
                ok = serviceAlive,
                okText = "已連線",
                failText = "尚未連線"
            )
            StatusRow(
                label = "覆蓋層權限",
                ok = overlayGranted,
                okText = "已授權",
                failText = "未授權",
                onAction = if (!overlayGranted) onRequestOverlay else null,
                actionText = "請求"
            )
            StatsRow(
                label = "最近偵測前景",
                value = diagnostic.lastForegroundPackage ?: "—"
            )
            StatsRow(
                label = "最近偵測時間",
                value = diagnostic.lastEventEpochMs?.let { formatClockTime(it) } ?: "—"
            )
            StatsRow(
                label = "最近判決",
                value = diagnostic.lastDecisionLabel ?: "—"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "事件數",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "總${diagnostic.totalEvents} / 忽${diagnostic.ignoredCount} / " +
                        "通${diagnostic.allowedCount} / 擋${diagnostic.blockedCount}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            OutlinedButton(
                onClick = onFireTestWarning,
                modifier = Modifier.fillMaxWidth(),
                shape = ButtonShape,
                border = AlertCardBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertYellow)
            ) {
                Text(text = "測試警告畫面")
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    okText: String,
    failText: String,
    onAction: (() -> Unit)? = null,
    actionText: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (ok) okText else failText,
                color = if (ok) SuccessGreen else WarningRed,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!ok && onAction != null && actionText != null) {
                TextButton(onClick = onAction) {
                    Text(text = actionText, color = AlertYellow)
                }
            }
        }
    }
}

private val clockTimeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private fun formatClockTime(epochMs: Long): String = clockTimeFormatter.format(Date(epochMs))

private fun dial165(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:165")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
