package com.anticyscam.app.ui.mainfunction

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.R
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.prefs.DailyAddTracker
import com.anticyscam.app.data.repository.TransferAccountRepository
import com.anticyscam.app.domain.model.TransferAccount
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

/**
 * Owns transfer-account state for the main screen. Triggers the one-time
 * default ("臨時用") seed on construction so the user always sees at least
 * that entry, even on a fresh install.
 *
 * Also surfaces the live [AntiScamClock] tick + the [DailyAddTracker] state
 * so cards and the add-dialog can render cooldown / dormancy / warning UI
 * without each one re-reading prefs.
 */
@HiltViewModel
class TransferAccountViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: TransferAccountRepository,
    private val clock: AntiScamClock,
    private val dailyTracker: DailyAddTracker
) : ViewModel() {

    val accounts: StateFlow<List<TransferAccount>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openCount: StateFlow<Int> = clock.observeOpenCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), clock.appOpenCount())

    val dailyState: StateFlow<DailyAddTracker.State> = dailyTracker.observeState()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            dailyTracker.snapshot()
        )

    val now: StateFlow<Long> = MutableStateFlow(clock.now()).asStateFlow()

    private val _addResult = MutableStateFlow<AddOutcome>(AddOutcome.Idle)
    val addResult: StateFlow<AddOutcome> = _addResult.asStateFlow()

    /** Live UI tuple consumed by the card list — pure state, no behaviour. */
    val uiList: StateFlow<List<AccountUi>> = combine(accounts, openCount) { list, opens ->
        list.map { acct ->
            AccountUi(account = acct, status = acct.status(clock.now(), opens))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureDefaultSeeded(appContext.getString(R.string.transfer_default_name))
        }
    }

    fun addAccount(name: String, accountNumber: String) {
        viewModelScope.launch {
            _addResult.value = repository.add(name, accountNumber).toOutcome()
        }
    }

    fun editAccount(id: Long, newName: String, newAccountNumber: String) {
        viewModelScope.launch {
            _addResult.value = repository
                .editAccount(id, newName, newAccountNumber)
                .toOutcome()
        }
    }

    private fun TransferAccountRepository.AddResult.toOutcome(): AddOutcome = when (this) {
        is TransferAccountRepository.AddResult.Success ->
            AddOutcome.Success(id, countToday, warning)
        TransferAccountRepository.AddResult.LimitReached -> AddOutcome.LimitReached
        TransferAccountRepository.AddResult.InvalidInput -> AddOutcome.InvalidInput
        is TransferAccountRepository.AddResult.DailyLimitTriggered ->
            AddOutcome.DailyLimitTriggered(lockUntil)
        is TransferAccountRepository.AddResult.DailyLocked ->
            AddOutcome.DailyLocked(remainingMs)
        TransferAccountRepository.AddResult.EditsExhausted -> AddOutcome.EditsExhausted
    }

    fun consumeAddResult() {
        _addResult.value = AddOutcome.Idle
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    data class AccountUi(
        val account: TransferAccount,
        val status: TransferAccount.Status
    )

    sealed interface AddOutcome {
        data object Idle : AddOutcome
        data class Success(val id: Long, val countToday: Int, val warning: Boolean) : AddOutcome
        data object LimitReached : AddOutcome
        data object InvalidInput : AddOutcome
        data class DailyLimitTriggered(val lockUntil: Long) : AddOutcome
        data class DailyLocked(val remainingMs: Long) : AddOutcome
        data object EditsExhausted : AddOutcome
    }
}
