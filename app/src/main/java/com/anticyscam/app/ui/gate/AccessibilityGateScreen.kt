package com.anticyscam.app.ui.gate

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anticyscam.app.R
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import com.anticyscam.app.ui.theme.SuccessGreen
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedDark
import com.anticyscam.app.utils.AccessibilityChecker
import com.anticyscam.app.utils.LaunchResult

/**
 * 三項保護要求 inline gate。原本是擋整個 App 的全螢幕 gate，自從需求調整為
 * 「進入 App 不擋、僅鎖防詐器 tab 功能」後，此 Composable 改為渲染在防詐器
 * 分頁裡：三項其中一項未啟用時顯示，三項全綠時防詐器主畫面才接管。
 *
 * - 三項並列清單：無障礙、裝置管理員、通知權限
 * - 各自附「前往啟用」按鈕，按下後跳到對應系統設定頁
 * - 回到 App 時上層 `onResume` 會呼叫 [AccessibilityGateViewModel.refresh]
 *   自動更新狀態
 */
@Composable
fun AccessibilityGateScreen(
    state: GateUiState,
    onOpenA11ySettings: () -> Unit = {},
    onOpenDeviceAdminSettings: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {}
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShieldBadge()

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.gate_inline_title),
                color = WarningRed,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.gate_inline_subtitle),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val a11yOpenHint = stringResource(R.string.gate_accessibility_open_hint)
                val adminFallbackHint = stringResource(R.string.gate_admin_fallback_hint)
                val batteryHint = stringResource(R.string.gate_battery_hint)
                val launchFailedTemplate = stringResource(R.string.gate_launch_failed)
                GateRow(
                    title = stringResource(R.string.gate_item_a11y),
                    desc = stringResource(R.string.gate_item_a11y_desc),
                    enabled = state.isServiceEnabled,
                    inlineHint = a11yOpenHint,
                    onAction = {
                        onOpenA11ySettings()
                        val result = AccessibilityChecker.launchA11ySettings(context)
                        showLaunchFailureToast(context, result, launchFailedTemplate)
                    }
                )
                GateRow(
                    title = stringResource(R.string.gate_item_admin),
                    desc = stringResource(R.string.gate_item_admin_desc),
                    enabled = state.isDeviceAdminActive,
                    inlineHint = adminFallbackHint,
                    onAction = {
                        onOpenDeviceAdminSettings()
                        val result = AccessibilityChecker.launchDeviceAdminEnable(context)
                        showLaunchFailureToast(context, result, launchFailedTemplate)
                    }
                )
                GateRow(
                    title = stringResource(R.string.gate_item_notify),
                    desc = stringResource(R.string.gate_item_notify_desc),
                    enabled = state.isNotificationsEnabled,
                    onAction = {
                        onOpenNotificationSettings()
                        runCatching {
                            context.startActivity(
                                AccessibilityChecker.openAppNotificationSettingsIntent(context)
                            )
                        }
                    }
                )
                GateRow(
                    title = stringResource(R.string.gate_item_battery),
                    desc = stringResource(R.string.gate_item_battery_desc),
                    enabled = state.isBatteryUnrestricted,
                    inlineHint = batteryHint,
                    onAction = {
                        onOpenBatterySettings()
                        runCatching {
                            context.startActivity(
                                AccessibilityChecker.requestBatteryOptimizationExemptionIntent(
                                    context.packageName
                                )
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.gate_all_ready_hint),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun showLaunchFailureToast(
    context: Context,
    result: LaunchResult,
    failureTemplate: String
) {
    if (result.launched) return
    val errName = result.error?.javaClass?.simpleName ?: "Unknown"
    Toast.makeText(context, failureTemplate.format(errName), Toast.LENGTH_LONG).show()
}

@Composable
private fun ShieldBadge() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(WarningRedDark)
            .border(2.dp, WarningRed, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Security,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun GateRow(
    title: String,
    desc: String,
    enabled: Boolean,
    onAction: () -> Unit,
    inlineHint: String? = null
) {
    val borderColor = if (enabled) SuccessGreen else WarningRedDark
    Surface(
        color = SurfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (enabled) SuccessGreen else WarningRed,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = desc,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (enabled) {
                    StatusPill(
                        label = stringResource(R.string.gate_status_on),
                        bg = SuccessGreen.copy(alpha = 0.2f),
                        fg = SuccessGreen
                    )
                } else {
                    Button(
                        onClick = onAction,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 6.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningRed,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.gate_action_enable),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (!enabled && !inlineHint.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = inlineHint,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, bg: Color, fg: Color) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun AccessibilityGatePreview() {
    AntiCyScamTheme {
        AccessibilityGateScreen(
            state = GateUiState(
                isServiceEnabled = true,
                isDeviceAdminActive = false,
                isNotificationsEnabled = false,
                isBatteryUnrestricted = false,
                hasCheckedOnce = true
            )
        )
    }
}
