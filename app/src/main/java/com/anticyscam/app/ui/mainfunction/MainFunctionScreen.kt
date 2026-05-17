package com.anticyscam.app.ui.mainfunction

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anticyscam.app.R
import com.anticyscam.app.domain.model.BoundApp
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.domain.model.TransferAccountState
import com.anticyscam.app.ui.lockdown.DailyAddLockActivity
import com.anticyscam.app.ui.tempuse.TempUseGateActivity
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedDark
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
    val transferViewModel: TransferAccountViewModel = hiltViewModel()
    val mainViewModel: MainFunctionViewModel = hiltViewModel()
    val uiList by transferViewModel.uiList.collectAsState()
    val addResult by transferViewModel.addResult.collectAsState()
    val dailyState by transferViewModel.dailyState.collectAsState()
    val dailyLockMs by transferViewModel.dailyLockRemainingMs.collectAsState()
    val isDailyLocked = dailyLockMs > 0L
    val boundApps by mainViewModel.boundApps.collectAsState()
    val pendingApp by mainViewModel.pendingApp.collectAsState()
    val tempUseUiState by transferViewModel.tempUseUiState.collectAsState()

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

    // Defence in depth: while the 1-hour ban is active, force-close any
    // already-open picker / add / edit surface. The Scaffold overlay below
    // also blocks taps, but closing modals here keeps the dismissed-from-ban
    // state idempotent across rotation and configuration changes.
    LaunchedEffect(tempUseUiState.isBanned) {
        if (tempUseUiState.isBanned) {
            mainViewModel.cancelPending()
            showAddDialog = false
            editingAccount = null
        }
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // BAN period: skip composing the underlying page entirely. The
            // overlay covers it visually anyway, but keeping the transfer list
            // + per-card 1s countdown alive underneath causes every-second
            // recomposition churn — felt as jank when the user switches tabs
            // because Compose has to tear down all that ticking state at the
            // same moment it builds the new tab.
            if (!tempUseUiState.isBanned) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Watchful (post-5-min-lockout) banner sits at the very top per
                // spec — orange bar with 10-min countdown.
                if (tempUseUiState.isWatchful) {
                    TempUseWatchfulBanner(remainingMs = tempUseUiState.watchfulRemainingMs)
                }

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
            // 1-hour ban overlay. Scoped to the 防詐器 tab's content area on
            // purpose — sits inside the Scaffold so it covers transfer/list
            // surfaces but stays underneath the tab bar, leaving 詐騙專區 and
            // other tabs reachable per spec.
            if (tempUseUiState.isBanned) {
                TempUseBannedOverlay(
                    remainingMs = tempUseUiState.banRemainingMs,
                    onCall165 = { dial165(context) }
                )
            }
        }
    }

    pendingApp?.takeIf { !tempUseUiState.isBanned }?.let { app ->
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
                    transferViewModel = transferViewModel,
                    snackbarHostState = snackbarHostState,
                    scope = scope,
                    copiedMsg = copiedMsg,
                    notInstalledMsg = notInstalledMsg
                )
            },
            onDismiss = mainViewModel::cancelPending
        )
    }

    if (showAddDialog && !tempUseUiState.isBanned) {
        AddTransferAccountDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, number, bankCode ->
                transferViewModel.addAccount(name, number, bankCode)
            },
            showSecondAddWarning = dailyState.countToday >= 2
        )
    }

    editingAccount?.takeIf { !tempUseUiState.isBanned }?.let { account ->
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
    transferViewModel: TransferAccountViewModel,
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
            // BAN escalation must NOT route through TempUseGateActivity — that
            // Activity covers the whole screen and hides the tab bar, defeating
            // the in-page TempUseBannedOverlay that intentionally keeps
            // 詐騙專區 / 設定 reachable. Commit the escalation locally and let
            // the next 1-sec tick raise the overlay instead.
            if (transferViewModel.tryShortCircuitForBan()) return
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

/**
 * Post-lockout 10-minute watchful window indicator. Orange bar (AlertYellow)
 * with black text — sits at the very top of the 防詐器 tab so it is the first
 * thing the user sees on entering. Communicates two facts:
 *  - the user is still under heightened scrutiny
 *  - hitting THIRD again inside this window escalates to a 1-hour BAN
 */
@Composable
private fun TempUseWatchfulBanner(remainingMs: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AlertYellow)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    R.string.temp_use_watchful_banner_title,
                    formatRemainingHms(remainingMs)
                ),
                color = SurfaceBlack,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.temp_use_watchful_banner_body),
                color = SurfaceBlack,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Full-screen 1-hour ban overlay. Lives inside the Scaffold so the tab bar
 * stays reachable — switching to 詐騙專區 / 設定 is intentionally NOT blocked
 * (only transfer-related flows are). The transparent backdrop swallows taps
 * via clickable {} so nothing underneath can receive input.
 */
@Composable
private fun TempUseBannedOverlay(remainingMs: Long, onCall165: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarningRedDark.copy(alpha = 0.97f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⛔",
                color = TextPrimary,
                fontSize = 56.sp
            )
            Text(
                text = stringResource(R.string.temp_use_banned_title),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    R.string.temp_use_banned_body,
                    formatRemainingHms(remainingMs)
                ),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalButton(
                onClick = onCall165,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White,
                    contentColor = WarningRedDark
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.temp_use_stage3_call_cta),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun dial165(context: Context) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:165"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
