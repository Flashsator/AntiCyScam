package com.anticyscam.app.ui.mainfunction

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anticyscam.app.R
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.WarningRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 反詐器主畫面。
 *
 * 兩段式啟動流程（Phase 6）：
 *  1. 使用者點擊某個綁定 App → ViewModel 設定 pendingApp → 顯示
 *     [TransferAccountPickerSheet]
 *  2. 使用者在 sheet 中點擊一筆帳號：
 *       - 一般帳號：複製帳號號碼到剪貼簿
 *       - 「臨時用」：不複製
 *       兩者皆會：呼叫 AuthorizedLaunchTracker.authorize() 並 startActivity
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFunctionScreen(onOpenBindApps: () -> Unit) {
    val transferViewModel: TransferAccountViewModel = hiltViewModel()
    val mainViewModel: MainFunctionViewModel = hiltViewModel()
    val accounts by transferViewModel.accounts.collectAsState()
    val addResult by transferViewModel.addResult.collectAsState()
    val boundApps by mainViewModel.boundApps.collectAsState()
    val pendingApp by mainViewModel.pendingApp.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val copiedMsg = stringResource(R.string.transfer_copied)
    val limitMsg = stringResource(R.string.transfer_max_reached)
    val notInstalledMsg = "目標 App 已被解除安裝"

    LaunchedEffect(addResult) {
        when (addResult) {
            is TransferAccountViewModel.AddOutcome.Success -> {
                showAddDialog = false
                transferViewModel.consumeAddResult()
            }
            TransferAccountViewModel.AddOutcome.LimitReached -> {
                snackbarHostState.showSnackbar(limitMsg)
                transferViewModel.consumeAddResult()
            }
            TransferAccountViewModel.AddOutcome.InvalidInput -> {
                transferViewModel.consumeAddResult()
            }
            TransferAccountViewModel.AddOutcome.Idle -> Unit
        }
    }

    Scaffold(
        containerColor = SurfaceBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = WarningRed,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增轉帳帳號")
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_main),
                color = WarningRed,
                style = MaterialTheme.typography.headlineMedium
            )

            BindAppsButton(onClick = onOpenBindApps)

            Text(
                text = "已綁定 App",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            BoundAppsRow(
                apps = boundApps,
                onAppClick = mainViewModel::onBoundAppClicked
            )

            Text(
                text = "轉帳帳號",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            TransferAccountList(
                accounts = accounts,
                onAccountClick = { account ->
                    handleStandaloneCopy(context, account, snackbarHostState, scope, copiedMsg)
                },
                onAccountDelete = transferViewModel::delete
            )
        }
    }

    pendingApp?.let { app ->
        TransferAccountPickerSheet(
            pendingApp = app,
            accounts = accounts,
            sheetState = sheetState,
            onAccountSelected = { account ->
                handleAuthorizedLaunch(
                    context = context,
                    account = account,
                    viewModel = mainViewModel,
                    snackbarHostState = snackbarHostState,
                    scope = scope,
                    copiedMsg = copiedMsg,
                    notInstalledMsg = notInstalledMsg
                )
            },
            onDismiss = mainViewModel::cancelPending
        )
    }

    if (showAddDialog) {
        AddTransferAccountDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, number -> transferViewModel.addAccount(name, number) }
        )
    }
}

@Composable
private fun BindAppsButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(width = 2.dp, color = WarningRed),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceBlack,
            contentColor = WarningRed
        )
    ) {
        Text(
            text = stringResource(R.string.bind_apps_button),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun TransferAccountList(
    accounts: List<TransferAccount>,
    onAccountClick: (TransferAccount) -> Unit,
    onAccountDelete: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = accounts, key = { it.id }) { account ->
            TransferAccountCard(
                account = account,
                onClick = onAccountClick,
                onDelete = { onAccountDelete(it.id) }
            )
        }
        if (accounts.isEmpty()) {
            items(1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "尚未建立任何轉帳帳號",
                        color = TextPrimary.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * 純複製模式 — 使用者在主清單上點擊帳號，沒有要啟動任何 App。
 */
private fun handleStandaloneCopy(
    context: Context,
    account: TransferAccount,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    copiedMsg: String
) {
    if (account.isDefault) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("transfer_account", account.accountNumber))
    scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
}

/**
 * 授權啟動模式 — 從 picker sheet 點擊帳號：複製（若非預設）+ launch。
 */
private fun handleAuthorizedLaunch(
    context: Context,
    account: TransferAccount,
    viewModel: MainFunctionViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    copiedMsg: String,
    notInstalledMsg: String
) {
    if (!account.isDefault) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("transfer_account", account.accountNumber))
        scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
    }
    when (viewModel.authorizeAndLaunch()) {
        is MainFunctionViewModel.LaunchOutcome.NotInstalled -> {
            scope.launch { snackbarHostState.showSnackbar(notInstalledMsg) }
        }
        is MainFunctionViewModel.LaunchOutcome.Launched -> Unit
        MainFunctionViewModel.LaunchOutcome.NoPending -> Unit
    }
}
