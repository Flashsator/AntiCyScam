package com.anticyscam.app.ui.bind

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anticyscam.app.domain.model.BindingState
import com.anticyscam.app.ui.components.AppIcon
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextDisabled
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed

/**
 * 綁定／解除 APP 頁面。
 *
 * 批次儲存流程（使用者規格「勾完一堆按儲存+yes」）：
 *  - 勾選 / 取消勾選 不會立刻寫入 DB
 *  - 按下「儲存」時，ViewModel 分類所有想取消勾選的列：
 *      - PendingMaturation（< 24h）→ 直接刪除，免冷靜期
 *      - Matured（≥ 24h）        → 跳出 CooldownUnbindDialog 一次性確認
 *  - 確認後一起寫入 + 關閉頁面
 *
 * PendingUnbind 列的特殊處理：
 *  - checkbox 鎖住（一律 checked），代表這列仍受保護
 *  - 點 checkbox 不會切換（VM toggle 早退）
 *  - 列尾顯示「取消解除」inline 連結，按下就把這列倒數歸零、回到 Matured
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindAppsScreen(onClose: () -> Unit) {
    val viewModel: BindAppsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = SurfaceBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "綁定／解除 APP",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceBlack,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = WarningRed,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "勾選您希望防詐器保護的 App（例如網銀、Line）。儲存後，這些 App 只能從防詐器內進入。",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    RecommendationHint()

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = state.apps, key = { "app:${it.packageName}" }) { app ->
                            BindableAppRow(
                                app = app,
                                onToggle = { viewModel.toggle(app.packageName) },
                                onCancelCooldown = {
                                    viewModel.onCancelCooldown(app.packageName)
                                }
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.onSaveClicked(onClose) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningRed,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(
                            text = "儲存（已選 ${state.selected.size}）",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }

    if (state.showCooldownDialog) {
        val labelByPkg = state.apps.associate { it.packageName to it.label }
        val labels = state.pendingMaturedUnbinds.map { labelByPkg[it] ?: it }
        CooldownUnbindDialog(
            appLabels = labels,
            onConfirm = { viewModel.onConfirmCooldownDialog(onClose) },
            onDismiss = { viewModel.onDismissCooldownDialog() }
        )
    }
}

@Composable
private fun RecommendationHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDim)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "建議綁定的 App",
            color = AlertYellow,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = "・您使用的網銀（CUBE、Richart、行動銀行…）",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "・支付工具",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BindableAppRow(
    app: BindableApp,
    onToggle: () -> Unit,
    onCancelCooldown: () -> Unit
) {
    val isCoolingDown = app.state is BindingState.PendingUnbind

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDim)
            .let { if (isCoolingDown) it else it.clickable(onClick = onToggle) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(
            packageName = app.packageName,
            modifier = Modifier.size(28.dp),
            fallbackTint = TextDisabled
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = app.packageName,
                color = TextDisabled,
                style = MaterialTheme.typography.bodySmall
            )
            StateFooter(state = app.state)
        }
        if (isCoolingDown) {
            TextButton(onClick = onCancelCooldown) {
                Text(
                    text = "取消解除",
                    color = AlertYellow,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        } else {
            Checkbox(
                checked = app.isBound,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = WarningRed,
                    uncheckedColor = TextSecondary,
                    checkmarkColor = TextPrimary,
                    disabledCheckedColor = AlertYellow
                )
            )
        }
    }
}

@Composable
private fun StateFooter(state: BindingState) {
    when (state) {
        is BindingState.PendingMaturation -> Text(
            text = "綁定中 ${formatHms(state.remainingMs)}",
            color = AlertYellow,
            style = MaterialTheme.typography.bodySmall
        )
        BindingState.Matured -> Text(
            text = "已綁定",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        is BindingState.PendingUnbind -> Text(
            text = "解除中 剩 ${formatHms(state.remainingMs)}",
            color = WarningRed,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
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
