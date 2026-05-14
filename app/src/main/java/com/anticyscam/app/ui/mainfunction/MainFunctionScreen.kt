package com.anticyscam.app.ui.mainfunction

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.anticyscam.app.R
import com.anticyscam.app.domain.model.BoundApp
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.domain.model.TransferAccountState
import com.anticyscam.app.ui.gate.AccessibilityGateScreen
import com.anticyscam.app.ui.gate.AccessibilityGateViewModel
import com.anticyscam.app.ui.lockdown.DailyAddLockActivity
import com.anticyscam.app.ui.tempuse.TempUseGateActivity
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.WarningRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 防詐器主畫面。
 *
 * 啟動流程（Phase 14）：
 *  1. 使用者點擊某個綁定 App → ViewModel 設定 pendingApp → 顯示 picker sheet
 *  2. 使用者在 sheet 中點擊一筆帳號 → 依 [TransferAccountState] 分流：
 *       - Default / PendingMaturation → 啟動 TempUseGateActivity（< 24h 視為臨時用）
 *       - Matured / PendingDeletion   → 直接複製帳號 + launchAuthorized
 *       PendingDeletion 仍可被使用 — 使用者下意識挑這筆代表還在用，UI 可改按
 *       「取消刪除」復原。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFunctionScreen(onOpenBindApps: () -> Unit) {
    val gateViewModel: AccessibilityGateViewModel = hiltViewModel()
    val gateState by gateViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) gateViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!gateState.allRequirementsMet) {
        AccessibilityGateScreen(state = gateState)
        return
    }

    val transferViewModel: TransferAccountViewModel = hiltViewModel()
    val mainViewModel: MainFunctionViewModel = hiltViewModel()
    val uiList by transferViewModel.uiList.collectAsState()
    val addResult by transferViewModel.addResult.collectAsState()
    val dailyState by transferViewModel.dailyState.collectAsState()
    val dailyLockMs by transferViewModel.dailyLockRemainingMs.collectAsState()
    val isDailyLocked = dailyLockMs > 0L
    val boundApps by mainViewModel.boundApps.collectAsState()
    val pendingApp by mainViewModel.pendingApp.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<TransferAccount?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val copiedMsg = stringResource(R.string.transfer_copied)
    val limitMsg = stringResource(R.string.transfer_max_reached)
    val notInstalledMsg = "目標 App 已被解除安裝"
    val editsExhaustedMsg = stringResource(R.string.transfer_edits_exhausted_toast)
    val editWindowClosedMsg = stringResource(R.string.transfer_edit_window_closed_toast)
    val dailyLockedTemplate = stringResource(R.string.daily_add_locked_remaining)

    LaunchedEffect(addResult) {
        when (val r = addResult) {
            is TransferAccountViewModel.AddOutcome.Success -> {
                showAddDialog = false
                editingAccount = null
                transferViewModel.consumeAddResult()
            }
            TransferAccountViewModel.AddOutcome.LimitReached -> {
                snackbarHostState.showSnackbar(limitMsg)
                transferViewModel.consumeAddResult()
            }
            TransferAccountViewModel.AddOutcome.InvalidInput -> {
                transferViewModel.consumeAddResult()
            }
            is TransferAccountViewModel.AddOutcome.DailyLimitTriggered -> {
                showAddDialog = false
                editingAccount = null
                context.startActivity(DailyAddLockActivity.newIntent(context))
                transferViewModel.consumeAddResult()
            }
            is TransferAccountViewModel.AddOutcome.DailyLocked -> {
                showAddDialog = false
                editingAccount = null
                snackbarHostState.showSnackbar(
                    String.format(dailyLockedTemplate, formatRemaining(r.remainingMs))
                )
                transferViewModel.consumeAddResult()
            }
            TransferAccountViewModel.AddOutcome.EditsExhausted -> {
                editingAccount = null
                snackbarHostState.showSnackbar(editsExhaustedMsg)
                transferViewModel.consumeAddResult()
            }
            TransferAccountViewModel.AddOutcome.EditWindowClosed -> {
                editingAccount = null
                snackbarHostState.showSnackbar(editWindowClosedMsg)
                transferViewModel.consumeAddResult()
            }
            TransferAccountViewModel.AddOutcome.Idle -> Unit
        }
    }

    Scaffold(
        containerColor = SurfaceBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_main),
                color = WarningRed,
                style = MaterialTheme.typography.titleLarge
            )

            BindAppsButton(onClick = onOpenBindApps)

            // "已綁定 App" heading dropped intentionally — the icon row below
            // is self-evident, and removing this line gives the transfer
            // list ~28dp more vertical breathing room.
            BoundAppsRow(
                apps = boundApps,
                onAppClick = mainViewModel::onBoundAppClicked
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "轉帳帳號",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (!isDailyLocked) {
                    FilledTonalButton(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = WarningRed,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("新增", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            // Spec #1: once daily limit triggers the 24h lockdown, hide the
            // 新增 input path entirely and replace it with a red notice that
            // ticks down live. Defence in depth — even if the button were
            // somehow tapped, the repository still rejects in DailyLocked.
            if (isDailyLocked) {
                DailyAddLockBanner(remainingMs = dailyLockMs)
            }
            TransferAccountList(
                items = uiList,
                onAccountClick = { account ->
                    handleStandaloneCopy(context, account, snackbarHostState, scope, copiedMsg)
                },
                onRequestDelete = transferViewModel::requestDelete,
                onCancelDelete = transferViewModel::cancelDelete,
                onAccountEdit = { account -> editingAccount = account },
                modifier = Modifier.weight(1f)
            )
        }
    }

    pendingApp?.let { app ->
        TransferAccountPickerSheet(
            pendingApp = app,
            items = uiList,
            sheetState = sheetState,
            onAccountSelected = { accountUi ->
                handlePickerSelection(
                    context = context,
                    accountUi = accountUi,
                    app = app,
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
            onConfirm = { name, number, bankCode ->
                transferViewModel.addAccount(name, number, bankCode)
            },
            showSecondAddWarning = dailyState.countToday >= 2
        )
    }

    editingAccount?.let { account ->
        AddTransferAccountDialog(
            onDismiss = { editingAccount = null },
            onConfirm = { name, number, bankCode ->
                transferViewModel.editAccount(account.id, name, number, bankCode)
            },
            isEditMode = true,
            initialName = account.name,
            initialAccountNumber = account.accountNumber,
            initialBankCode = account.bankCode.orEmpty()
        )
    }
}

@Composable
private fun BindAppsButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
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
    items: List<TransferAccountViewModel.AccountUi>,
    onAccountClick: (TransferAccount) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onCancelDelete: (Long) -> Unit,
    onAccountEdit: (TransferAccount) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = items, key = { it.account.id }) { ui ->
            TransferAccountCard(
                account = ui.account,
                state = ui.state,
                onClick = onAccountClick,
                onRequestDelete = { onRequestDelete(it.id) },
                onCancelDelete = { onCancelDelete(it.id) },
                onEdit = onAccountEdit
            )
        }
        if (items.isEmpty()) {
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

private fun handlePickerSelection(
    context: Context,
    accountUi: TransferAccountViewModel.AccountUi,
    app: BoundApp,
    viewModel: MainFunctionViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    copiedMsg: String,
    notInstalledMsg: String
) {
    val account = accountUi.account
    when (accountUi.state) {
        TransferAccountState.Default,
        is TransferAccountState.PendingMaturation -> {
            viewModel.cancelPending()
            context.startActivity(
                TempUseGateActivity.newIntent(
                    context = context,
                    targetPackage = app.packageName,
                    accountId = if (account.isDefault) null else account.id,
                    accountNumber = if (account.isDefault) "" else account.accountNumber
                )
            )
        }
        TransferAccountState.Matured,
        is TransferAccountState.PendingDeletion -> {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("transfer_account", account.accountNumber))
            scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
            when (viewModel.authorizeAndLaunch()) {
                is MainFunctionViewModel.LaunchOutcome.NotInstalled -> {
                    scope.launch { snackbarHostState.showSnackbar(notInstalledMsg) }
                }
                is MainFunctionViewModel.LaunchOutcome.Launched -> Unit
                MainFunctionViewModel.LaunchOutcome.NoPending -> Unit
            }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0L)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h${m}m" else "${m}m"
}

private fun formatRemainingHms(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
private fun DailyAddLockBanner(remainingMs: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    ) {
        Text(
            text = "今日新增上限已達 — 剩 ${formatRemainingHms(remainingMs)}",
            color = WarningRed,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
