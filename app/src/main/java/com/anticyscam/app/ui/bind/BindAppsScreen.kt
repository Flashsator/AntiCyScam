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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextDisabled
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed

/**
 * 綁定／解除 APP 頁面。
 *
 * - 顯示手機已安裝可啟動的 App（已過濾自身）
 * - Checkbox 多選；勾選/取消即時更新 ViewModel 狀態
 * - 「儲存」按鈕一次寫入 repository，然後關閉頁面
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
                            imageVector = Icons.Filled.ArrowBack,
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
                        text = "勾選您希望反詐器保護的 App（例如網銀、Line）。儲存後，這些 App 只能從反詐器內進入。",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = state.apps, key = { it.packageName }) { app ->
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
private fun BindableAppRow(app: BindableApp, onToggle: () -> Unit) {
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
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = null,
            tint = TextDisabled,
            modifier = Modifier.size(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(
                text = app.packageName,
                color = TextDisabled,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Checkbox(
            checked = app.isBound,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = WarningRed,
                uncheckedColor = TextSecondary,
                checkmarkColor = TextPrimary
            )
        )
    }
}
