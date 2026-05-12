package com.anticyscam.app.ui.mainfunction

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.R
import com.anticyscam.app.data.repository.TransferAccountRepository
import com.anticyscam.app.domain.model.TransferAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns transfer-account state for the main screen. Triggers the one-time
 * default ("臨時用") seed on construction so the user always sees at least
 * that entry, even on a fresh install.
 */
@HiltViewModel
class TransferAccountViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: TransferAccountRepository
) : ViewModel() {

    val accounts: StateFlow<List<TransferAccount>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _addResult = MutableStateFlow<AddOutcome>(AddOutcome.Idle)
    val addResult: StateFlow<AddOutcome> = _addResult.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultSeeded(appContext.getString(R.string.transfer_default_name))
        }
    }

    fun addAccount(name: String, accountNumber: String) {
        viewModelScope.launch {
            _addResult.value = when (val result = repository.add(name, accountNumber)) {
                is TransferAccountRepository.AddResult.Success -> AddOutcome.Success(result.id)
                TransferAccountRepository.AddResult.LimitReached -> AddOutcome.LimitReached
                TransferAccountRepository.AddResult.InvalidInput -> AddOutcome.InvalidInput
            }
        }
    }

    fun consumeAddResult() {
        _addResult.value = AddOutcome.Idle
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    sealed interface AddOutcome {
        data object Idle : AddOutcome
        data class Success(val id: Long) : AddOutcome
        data object LimitReached : AddOutcome
        data object InvalidInput : AddOutcome
    }
}
