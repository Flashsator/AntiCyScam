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
import com.anticyscam.app.ui.dormant.DormantVerifyActivity
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
 * 兩段式啟動流程（Phase 6 + PRD § 3.3 / 3.4）：
 *  1. 使用者點擊某個綁定 App → ViewModel 設定 pendingApp → 顯示
 *     [TransferAccountPickerSheet]
 *  2. 使用者在 sheet 中點擊一筆帳號 → 依該帳號的 [TransferAccount.Status] 分流：
 *       - Default / InCooldown → 啟動 [TempUseGateActivity] 走三階段警告
 *       - Dormant              → 啟動 [DormantVerifyActivity] 一次性確認
 *       - Normal               → 複製帳號 + 直接 launchAuthorized
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFunctionScreen(onOpenBindApps: () -> Unit) {
    // Requirement #3 (revised): the 防詐器 tab is locked behind a three-way
    // gate (a11y + device admin + notifications). The other tabs (詐騙專區,
    // 設定) are unaffected and continue to render normally. When any of the
    // three requirements is missing, render the inline gate instead of the
    // transfer-account workflow below.
    val gateViewModel: AccessibilityGateViewModel = hiltViewModel()
    val gateState by gateViewModel.state.collectAsState()
    // 此 hiltViewModel() 是 NavBackStackEntry-scoped，跟 MainActivity 的
    // gateViewModel 不是同一個 instance — Activity.onResume 刷不到這裡。
    // 自己掛 lifecycle observer，每次回到前景就 refresh 一次，使用者從系統
    // 設定回 App 才能即時看到「已啟用」。
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "轉帳帳號",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
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
            TransferAccountList(
                items = uiList,
                onAccountClick = { account ->
                    handleStandaloneCopy(context, account, snackbarHostState, scope, copiedMsg)
                },
                onAccountDelete = transferViewModel::delete,
                onAccountEdit = { account -> editingAccount = account }
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
            onConfirm = { name, number -> transferViewModel.addAccount(name, number) },
            showSecondAddWarning = dailyState.countToday >= 2
        )
    }

    editingAccount?.let { account ->
        AddTransferAccountDialog(
            onDismiss = { editingAccount = null },
            onConfirm = { name, number ->
                transferViewModel.editAccount(account.id, name, number)
            },
            isEditMode = true,
            initialName = account.name,
            initialAccountNumber = account.accountNumber
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
    items: List<TransferAccountViewModel.AccountUi>,
    onAccountClick: (TransferAccount) -> Unit,
    onAccountDelete: (Long) -> Unit,
    onAccountEdit: (TransferAccount) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = items, key = { it.account.id }) { ui ->
            TransferAccountCard(
                account = ui.account,
                status = ui.status,
                onClick = onAccountClick,
                onDelete = { onAccountDelete(it.id) },
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
 * 從 picker sheet 點擊帳號：依 status 決定走哪條路。Stage gating / dormant
 * 確認都由各自的 Activity 內部負責 consume + launch；Normal 帳號才走原本
 * 「直接 copy + launchAuthorized」的快路徑。
 */
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
    when (accountUi.status) {
        TransferAccount.Status.Default,
        is TransferAccount.Status.InCooldown -> {
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
        TransferAccount.Status.Dormant -> {
            viewModel.cancelPending()
            context.startActivity(
                DormantVerifyActivity.newIntent(
                    context = context,
                    accountId = account.id,
                    targetPackage = app.packageName,
                    accountNumber = account.accountNumber
                )
            )
        }
        TransferAccount.Status.Normal -> {
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
