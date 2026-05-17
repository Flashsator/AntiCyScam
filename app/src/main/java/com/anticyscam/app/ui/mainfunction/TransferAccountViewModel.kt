package com.anticyscam.app.ui.mainfunction

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.R
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.prefs.DailyAddTracker
import com.anticyscam.app.data.prefs.NowSnapshot
import com.anticyscam.app.data.prefs.TempUseTracker
import com.anticyscam.app.data.repository.TransferAccountRepository
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.domain.model.TransferAccountState
import com.anticyscam.app.service.AppLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransferAccountViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: TransferAccountRepository,
    private val clock: AntiScamClock,
    private val dailyTracker: DailyAddTracker,
    private val tempUseTracker: TempUseTracker,
    private val appLauncher: AppLauncher
) : ViewModel() {

    val accounts: StateFlow<List<TransferAccount>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dailyState: StateFlow<DailyAddTracker.State> = dailyTracker.observeState()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            dailyTracker.snapshot()
        )

    /**
     * Per-second clock pulse used purely to re-derive card state so countdown
     * labels (綁定中 / 解除中 hh:mm:ss) update without user interaction.
     * Side-effects (sweep, DB writes) MUST NOT hang off this tick — see
     * memory: scroll-perf-rule. The sweep runs on its own 30s ticker below.
     */
    private val tick: StateFlow<NowSnapshot> = bindingTickerFlow(clock, 1_000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), clock.snapshot())

    private val _addResult = MutableStateFlow<AddOutcome>(AddOutcome.Idle)
    val addResult: StateFlow<AddOutcome> = _addResult.asStateFlow()

    val uiList: StateFlow<List<AccountUi>> = combine(accounts, tick) { list, now ->
        list.map { acct ->
            AccountUi(account = acct, state = repository.stateOf(acct, now))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Live remaining millis of the daily-add 24h lockdown, recomputed on the
     * per-second [tick]. 0 means not locked. Drives the inline red countdown
     * banner that replaces the 新增 button on the main screen — spec #1.
     */
    val dailyLockRemainingMs: StateFlow<Long> = combine(dailyState, tick) { state, now ->
        state.remainingLockMs(now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /**
     * Drives the post-3rd escalation surfaces on the main screen:
     *  - [TempUseUiState.isBanned]  → render full 1-hour ban overlay; block
     *    every transfer action (new/edit/picker/launch).
     *  - [TempUseUiState.isWatchful] → render orange warning bar with the
     *    10-min countdown above the transfer list.
     *
     * The 1-sec tick double-duties as a sweeper: each emission calls
     * [TempUseTracker.clearLockoutIfElapsed], so the LOCKED_OUT → WATCHFUL
     * and BANNED → reset transitions happen automatically even if the user
     * is just idling on the main screen.
     */
    val tempUseUiState: StateFlow<TempUseUiState> = tick.map { now ->
        tempUseTracker.clearLockoutIfElapsed()
        val snap = tempUseTracker.snapshot()
        when {
            snap.stage == TempUseTracker.Stage.BANNED -> TempUseUiState(
                isBanned = true,
                banRemainingMs = (snap.banUntil - now.wallMillis).coerceAtLeast(0L)
            )
            snap.watchfulUntil > 0L -> TempUseUiState(
                isWatchful = true,
                watchfulRemainingMs = (snap.watchfulUntil - now.wallMillis).coerceAtLeast(0L)
            )
            else -> TempUseUiState()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TempUseUiState())

    init {
        viewModelScope.launch {
            repository.ensureDefaultSeeded(appContext.getString(R.string.transfer_default_name))
        }
        // Independent 30s sweeper. The 48h cooldown only needs to land
        // within tens-of-seconds of expiry — far slower than the 1s UI
        // tick that drives countdown text.
        viewModelScope.launch {
            bindingTickerFlow(clock, SWEEP_INTERVAL_MS).collect {
                repository.sweepAutoDeletes()
            }
        }
    }

    fun addAccount(name: String, accountNumber: String, bankCode: String?) {
        viewModelScope.launch {
            _addResult.value = repository.add(name, accountNumber, bankCode).toOutcome()
        }
    }

    fun editAccount(
        id: Long,
        newName: String,
        newAccountNumber: String,
        newBankCode: String?
    ) {
        viewModelScope.launch {
            _addResult.value = repository
                .editAccount(id, newName, newAccountNumber, newBankCode)
                .toOutcome()
        }
    }

    fun requestDelete(id: Long) {
        viewModelScope.launch { repository.requestDelete(id) }
    }

    fun cancelDelete(id: Long) {
        viewModelScope.launch { repository.cancelDelete(id) }
    }

    fun consumeAddResult() {
        _addResult.value = AddOutcome.Idle
    }

    /**
     * Routes a "臨時用" account tap to one of three surfaces:
     *  - [TempUseRoute.BANNED] — caller MUST NOT launch anything; the in-page
     *    [TempUseBannedOverlay] handles the user. Collapses two cases: already
     *    BANNED, or THIRD-during-watchful (escalated to BAN here so the overlay
     *    raises on the next 1-sec tick instead of routing through the gate
     *    Activity, which would render BAN full-screen and hide the tab bar).
     *  - [TempUseRoute.STAGE1_DIALOG] — first hit in the 10-min window. Shown
     *    as a lightweight in-app dialog (no full-screen takeover, no kiosk):
     *    the user can only reach a bound app via 防詐器 anyway, so a gentle
     *    reminder is enough at this stage.
     *  - [TempUseRoute.GATE_ACTIVITY] — SECOND / THIRD-without-watchful /
     *    LOCKED_OUT. These keep the full-screen [TempUseGateActivity]; the
     *    forced-calm and lockdown stages need the kiosk takeover.
     */
    fun resolveTempUseRoute(): TempUseRoute {
        tempUseTracker.clearLockoutIfElapsed()
        val snap = tempUseTracker.snapshot()
        if (snap.stage == TempUseTracker.Stage.BANNED) return TempUseRoute.BANNED
        if (snap.stage == TempUseTracker.Stage.THIRD && snap.watchfulUntil > 0L) {
            tempUseTracker.consume()
            return TempUseRoute.BANNED
        }
        return if (snap.stage == TempUseTracker.Stage.FIRST) {
            TempUseRoute.STAGE1_DIALOG
        } else {
            TempUseRoute.GATE_ACTIVITY
        }
    }

    /**
     * Commits the first temp-use hit and launches the bound app. Mirrors the
     * Stage 1 proceed path that previously lived in TempUseGateViewModel:
     * consume() advances the 10-min counter, the account number is copied to
     * the clipboard, then the target package is launched with authorization.
     * Cancelling the dialog must NOT call this — keeping the attempt off the
     * counter matches the old Activity's Stage-1 back-press behaviour.
     */
    fun consumeFirstAndLaunch(targetPackage: String?, accountNumber: String) {
        tempUseTracker.consume()
        if (accountNumber.isNotBlank()) {
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("transfer_account", accountNumber))
        }
        targetPackage?.let { appLauncher.launchAuthorized(it) }
    }

    private fun TransferAccountRepository.AddResult.toOutcome(): AddOutcome = when (this) {
        is TransferAccountRepository.AddResult.Success ->
            AddOutcome.Success(id, countToday, warning)
        TransferAccountRepository.AddResult.LimitReached -> AddOutcome.LimitReached
        TransferAccountRepository.AddResult.InvalidInput -> AddOutcome.InvalidInput
        is TransferAccountRepository.AddResult.DailyLimitTriggered ->
            AddOutcome.DailyLimitTriggered(remainingMs)
        is TransferAccountRepository.AddResult.DailyLocked ->
            AddOutcome.DailyLocked(remainingMs)
        TransferAccountRepository.AddResult.EditsExhausted -> AddOutcome.EditsExhausted
        TransferAccountRepository.AddResult.EditWindowClosed -> AddOutcome.EditWindowClosed
    }

    data class AccountUi(
        val account: TransferAccount,
        val state: TransferAccountState
    )

    /** Where a temp-use account tap should be routed — see [resolveTempUseRoute]. */
    enum class TempUseRoute { BANNED, STAGE1_DIALOG, GATE_ACTIVITY }

    /**
     * Drives the post-3rd escalation surfaces on the main screen. The two
     * states are mutually exclusive — when [isBanned] is true the banner
     * is hidden and the overlay takes the whole screen; when [isWatchful]
     * is true only the top orange bar shows.
     */
    data class TempUseUiState(
        val isBanned: Boolean = false,
        val banRemainingMs: Long = 0L,
        val isWatchful: Boolean = false,
        val watchfulRemainingMs: Long = 0L
    )

    sealed interface AddOutcome {
        data object Idle : AddOutcome
        data class Success(val id: Long, val countToday: Int, val warning: Boolean) : AddOutcome
        data object LimitReached : AddOutcome
        data object InvalidInput : AddOutcome
        data class DailyLimitTriggered(val remainingMs: Long) : AddOutcome
        data class DailyLocked(val remainingMs: Long) : AddOutcome
        data object EditsExhausted : AddOutcome
        data object EditWindowClosed : AddOutcome
    }

    companion object {
        private const val SWEEP_INTERVAL_MS = 30_000L
    }
}
