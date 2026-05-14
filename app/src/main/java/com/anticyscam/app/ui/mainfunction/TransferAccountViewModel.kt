package com.anticyscam.app.ui.mainfunction

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.R
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.prefs.DailyAddTracker
import com.anticyscam.app.data.prefs.NowSnapshot
import com.anticyscam.app.data.repository.TransferAccountRepository
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.domain.model.TransferAccountState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransferAccountViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: TransferAccountRepository,
    private val clock: AntiScamClock,
    private val dailyTracker: DailyAddTracker
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
