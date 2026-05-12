package com.anticyscam.app.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.data.repository.TransferAccountRepository
import com.anticyscam.app.service.AuthorizedLaunchTracker
import com.anticyscam.app.utils.AccessibilityChecker
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

/**
 * Settings screen state + actions.
 *
 * Two state surfaces:
 *  - [status] is a derived StateFlow combining bound-app count, transfer-account
 *    count, and the live accessibility-service status. The a11y status is
 *    refreshed via [refreshAccessibilityStatus] because there is no Android
 *    callback for "user toggled our service in system settings" — we re-read
 *    on resume.
 *  - [pendingClear] surfaces a one-shot confirmation flag for the destructive
 *    "clear all data" action.
 */
@HiltViewModel
class SettingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val boundAppRepository: BoundAppRepository,
    private val transferAccountRepository: TransferAccountRepository,
    private val authorizedLaunchTracker: AuthorizedLaunchTracker
) : ViewModel() {

    private val accessibilityEnabled = MutableStateFlow(
        AccessibilityChecker.isOurServiceEnabled(appContext)
    )

    val status: StateFlow<SettingStatus> = combine(
        boundAppRepository.observeBoundApps().map { it.size },
        transferAccountRepository.observeAccounts().map { list ->
            list.count { !it.isDefault }
        },
        accessibilityEnabled
    ) { boundCount, userAccountCount, a11yOn ->
        SettingStatus(
            accessibilityEnabled = a11yOn,
            boundAppCount = boundCount,
            transferAccountCount = userAccountCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingStatus()
    )

    private val _pendingClear = MutableStateFlow(false)
    val pendingClear: StateFlow<Boolean> = _pendingClear.asStateFlow()

    fun refreshAccessibilityStatus() {
        accessibilityEnabled.value = AccessibilityChecker.isOurServiceEnabled(appContext)
    }

    fun requestClearAll() {
        _pendingClear.value = true
    }

    fun cancelClearAll() {
        _pendingClear.value = false
    }

    /**
     * Wipes user-owned data: bound apps + non-default transfer accounts.
     * The default "臨時用" is re-seeded by [TransferAccountViewModel.init]
     * on the next entry to the main screen.
     */
    fun confirmClearAll(onDone: () -> Unit) {
        viewModelScope.launch {
            transferAccountRepository.clear()
            boundAppRepository.replaceAll(emptyList())
            authorizedLaunchTracker.clearAll()
            _pendingClear.value = false
            onDone()
        }
    }

    data class SettingStatus(
        val accessibilityEnabled: Boolean = false,
        val boundAppCount: Int = 0,
        val transferAccountCount: Int = 0
    )
}
