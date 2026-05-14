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
 * Drives the four-requirement gate that protects the 防詐器 tab.
 *
 * Four requirements must hold simultaneously before the tab unlocks AND the
 * "防詐器保護中" foreground notification is allowed to appear:
 *   1. Accessibility service enabled
 *   2. Device admin active (prevents accidental uninstall)
 *   3. App-level notifications enabled (so the ongoing notification can show)
 *   4. Battery optimization ignored — without this, doze/standby on Android 12+
 *      and OEM aggressive kill silently terminate the a11y monitor in背景。
 *
 * State is recomputed on every [refresh] call. Callers drive this from
 * `onResume` so returning from the relevant system Settings page immediately
 * reflects the new state.
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
        val ctx = getApplication<Application>()
        val a11y = AccessibilityChecker.isOurServiceEnabled(ctx)
        val admin = AccessibilityChecker.isDeviceAdminActive(ctx)
        val notify = AccessibilityChecker.isNotificationsEnabled(ctx)
        val battery = AccessibilityChecker.isBatteryOptimizationIgnored(ctx)
        _state.value = GateUiState(
            isServiceEnabled = a11y,
            isDeviceAdminActive = admin,
            isNotificationsEnabled = notify,
            isBatteryUnrestricted = battery,
            hasCheckedOnce = true
        )
        if (a11y) {
            viewModelScope.launch {
                preferences.markFirstLaunchComplete()
            }
        }
    }
}

data class GateUiState(
    val isServiceEnabled: Boolean = false,
    val isDeviceAdminActive: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val isBatteryUnrestricted: Boolean = false,
    /** First check has happened — used to avoid flashing wrong state on launch. */
    val hasCheckedOnce: Boolean = false
) {
    val allRequirementsMet: Boolean
        get() = isServiceEnabled &&
            isDeviceAdminActive &&
            isNotificationsEnabled &&
            isBatteryUnrestricted
}
