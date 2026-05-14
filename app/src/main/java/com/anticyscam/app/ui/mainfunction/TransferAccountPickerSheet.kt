package com.anticyscam.app.ui.mainfunction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anticyscam.app.domain.model.BoundApp
import com.anticyscam.app.ui.theme.SurfaceElevated
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary

/**
 * Bottom sheet shown after the user taps a bound app. Forces a deliberate
 * choice — either an existing transfer recipient (account number copied)
 * or 「臨時用」(三階段警告). Dismissing without picking cancels the launch.
 *
 * 每張卡片帶有 status 徽章（🟢/🟡/💤/預設），讓使用者在分流前就看到帳號的
 * 風險狀態。實際的路由邏輯由父層的 [handlePickerSelection] 處理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferAccountPickerSheet(
    pendingApp: BoundApp,
    items: List<TransferAccountViewModel.AccountUi>,
    sheetState: SheetState,
    onAccountSelected: (TransferAccountViewModel.AccountUi) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "準備進入 ${pendingApp.label}",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "請先選擇要轉帳的對象，防詐器會複製對應帳號並開啟 App。若僅是登入查看，請選「臨時用」。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = items, key = { it.account.id }) { ui ->
                    TransferAccountCard(
                        account = ui.account,
                        status = ui.status,
                        onClick = { onAccountSelected(ui) },
                        onDelete = { /* deletion is not allowed in this picker */ },
                        showDelete = false
                    )
                }
            }
        }
    }
}
