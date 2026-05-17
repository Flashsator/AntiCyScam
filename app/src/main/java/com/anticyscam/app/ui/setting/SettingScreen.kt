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
 *  1. 防詐器保護設定（偵測權限 + 選用的電池白名單；onResume 重新檢查）
 *  2. 統計：已綁定 App 數 / 已建立轉帳帳號數
 *  3. 165 反詐騙專線（撥號 Intent）
 *  4. 關於：版本資訊
 */
@Composable
fun SettingScreen() {
    val viewModel: SettingViewModel = hiltViewModel()
    val status by viewModel.status.collectAsState()
    val diagnostic by viewModel.diagnostic.collectAsState()
    val catalogMeta by viewModel.catalogMeta.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus()
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
                    usageStatsGranted = status.usageStatsGranted,
                    overlayGranted = status.overlayPermissionGranted,
                    batteryWhitelisted = status.batteryOptimizationIgnored,
                    notificationsGranted = status.notificationsEnabled,
                    onOpenUsageAccess = viewModel::openUsageAccessSettings,
                    onOpenOverlay = viewModel::requestOverlayPermission,
                    onRequestBattery = viewModel::requestBatteryExemption,
                    onOpenNotification = viewModel::openNotificationSettings
                )
            }
            item {
                DiagnosticCard(
                    diagnostic = diagnostic,
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

/**
 * 防詐器保護設定卡。
 *
 * 偵測必需的兩項系統權限：使用情況存取權、上層顯示。再加上兩項選用項目：
 * 「電池白名單」與「通知」。Android 不允許 App 直接代開這些系統權限，因此
 * 「開關」實際是狀態列 +「前往啟用」捷徑，點下後跳系統設定；每項下方附說明
 * 文字解釋用途。
 *
 * 卡片邊框：兩項偵測權限都到位時顯示綠色；電池白名單與通知為選用項目，
 * 不影響邊框顏色。
 */
@Composable
private fun ProtectionStatusCard(
    usageStatsGranted: Boolean,
    overlayGranted: Boolean,
    batteryWhitelisted: Boolean,
    notificationsGranted: Boolean,
    onOpenUsageAccess: () -> Unit,
    onOpenOverlay: () -> Unit,
    onRequestBattery: () -> Unit,
    onOpenNotification: () -> Unit
) {
    val detectionReady = usageStatsGranted && overlayGranted
    val borderColor = if (detectionReady) SuccessGreen else AlertYellow
    val border = remember(borderColor) { BorderStroke(1.dp, borderColor) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = border
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector =
                        if (detectionReady) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = borderColor
                )
                Text(
                    text = stringResource(R.string.setting_protection_title),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            ProtectionFeatureBlock(
                label = stringResource(R.string.setting_feature_usage),
                description = stringResource(R.string.setting_feature_usage_desc),
                enabled = usageStatsGranted,
                onOpen = onOpenUsageAccess
            )
            ProtectionFeatureBlock(
                label = stringResource(R.string.setting_feature_overlay),
                description = stringResource(R.string.setting_feature_overlay_desc),
                enabled = overlayGranted,
                onOpen = onOpenOverlay
            )
            ProtectionFeatureBlock(
                label = stringResource(R.string.setting_feature_battery),
                description = stringResource(R.string.setting_feature_battery_desc),
                enabled = batteryWhitelisted,
                onOpen = onRequestBattery
            )
            ProtectionFeatureBlock(
                label = stringResource(R.string.setting_feature_notification),
                description = stringResource(R.string.setting_feature_notification_desc),
                enabled = notificationsGranted,
                onOpen = onOpenNotification
            )
        }
    }
}

/**
 * 單一保護功能區塊：狀態列 +「開啟後效果」說明文字。
 *
 * [isWarning] 為 true 時，下方說明文字改用 [WarningRed] 紅色小字 —— 用於
 * 無障礙服務這類「開啟後行為強烈、需提醒風險」的項目。
 */
@Composable
private fun ProtectionFeatureBlock(
    label: String,
    description: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    isWarning: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProtectionFeatureRow(
            label = label,
            enabled = enabled,
            onOpen = onOpen,
            isWarning = isWarning
        )
        Text(
            text = description,
            color = if (isWarning) WarningRed else TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ProtectionFeatureRow(
    label: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    isWarning: Boolean = false
) {
    // 未啟用時的提示色：無障礙服務（isWarning）維持紅色強調風險，
    // 使用情況存取權／上層顯示則用黃色 —— 純屬後備權限，紅色過於危言聳聽。
    val pendingColor = if (isWarning) WarningRed else AlertYellow
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
                tint = if (enabled) SuccessGreen else pendingColor
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
                    color = pendingColor
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
                text = "回報誤判、想法或遇到的問題，幫助我改善這個 App。",
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

/**
 * 診斷資訊卡 —— 純偵測紀錄。
 *
 * 權限狀態（無障礙服務／使用情況存取權／上層顯示）已集中在上方「防詐器保護
 * 設定」卡，這裡不再重複；只保留前景偵測的即時紀錄與測試入口，供「綁定 App
 * 直開卻沒跳警告」時排查。
 */
@Composable
private fun DiagnosticCard(
    diagnostic: ForegroundAppGuard.Diagnostic,
    onFireTestWarning: () -> Unit
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
            Text(
                text = "診斷資訊",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "若綁定 App 直開時沒跳警告，可參考下方最近偵測紀錄排查問題。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
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
