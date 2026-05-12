package com.anticyscam.app.ui.gate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.prefs.AppPreferences
import com.anticyscam.app.utils.AccessibilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the accessibility gate. The gate cannot be passed unless the system
 * reports our accessibility service as enabled.
 *
 * State is recomputed on every [refresh] call. MainActivity drives this from
 * onResume so that returning from the system Accessibility settings page
 * immediately reflects the new state.
 */
@HiltViewModel
class AccessibilityGateViewModel @Inject constructor(
    application: Application,
    private val preferences: AppPreferences
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(GateUiState())
    val state: StateFlow<GateUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val enabled = AccessibilityChecker.isOurServiceEnabled(getApplication())
        _state.value = _state.value.copy(
            isServiceEnabled = enabled,
            hasCheckedOnce = true
        )
        if (enabled) {
            viewModelScope.launch {
                preferences.markFirstLaunchComplete()
            }
        }
    }
}

data class GateUiState(
    val isServiceEnabled: Boolean = false,
    /** First check has happened — used to avoid flashing wrong state on launch. */
    val hasCheckedOnce: Boolean = false
)
