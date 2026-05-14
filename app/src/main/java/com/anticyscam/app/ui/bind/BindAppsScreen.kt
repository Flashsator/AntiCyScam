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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
 * Phase I: 不再列出寫死的銀行 App 清單（包名/品牌名差異太大，列了找不到反而誤導）。
 * 改成純文字提示，列出常見銀行 App 名稱讓使用者自己在清單中找。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindAppsScreen(onClose: () -> Unit) {
    val viewModel: BindAppsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeToast()
    }

    Scaffold(
        containerColor = SurfaceBlack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                                onToggle = { viewModel.toggle(app.packageName) }
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.save(onClose) },
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
            text = "常見銀行 App 提示",
            color = AlertYellow,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = "不同銀行 App 名稱差異很大（CUBE、Wallet、Richart、行動銀行…），請在下方清單找到您使用的網銀並勾選；Line、Messenger 等通訊 App 也建議納入保護。",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BindableAppRow(app: BindableApp, onToggle: () -> Unit) {
    val now = System.currentTimeMillis()
    val unlockableAt = app.unlockableAt
    val isLocked = app.isBound && unlockableAt != null && now < unlockableAt

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDim)
            .clickable(onClick = onToggle)
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
            Text(text = app.label, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(
                text = app.packageName,
                color = TextDisabled,
                style = MaterialTheme.typography.bodySmall
            )
            if (isLocked && unlockableAt != null) {
                val remainingMs = unlockableAt - now
                Text(
                    text = "🔒 ${formatUnbindLock(remainingMs)} 後可解除",
                    color = AlertYellow,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
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

private fun formatUnbindLock(remainingMs: Long): String {
    val hours = remainingMs / (60L * 60 * 1000)
    val mins = (remainingMs / (60L * 1000)) % 60
    return when {
        hours > 0 -> "${hours}小時${mins}分"
        mins > 0 -> "${mins}分"
        else -> "1 分以內"
    }
}
