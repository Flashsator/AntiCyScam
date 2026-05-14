package com.anticyscam.app.ui.setting

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.data.repository.TransferAccountRepository
import com.anticyscam.app.service.AntiScamAccessibilityService
import com.anticyscam.app.service.AuthorizedLaunchTracker
import com.anticyscam.app.service.ForegroundAppGuard
import com.anticyscam.app.ui.warning.BlockingWarningActivity
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
    private val authorizedLaunchTracker: AuthorizedLaunchTracker,
    private val foregroundAppGuard: ForegroundAppGuard
) : ViewModel() {

    private val accessibilityEnabled = MutableStateFlow(
        AccessibilityChecker.isOurServiceEnabled(appContext)
    )

    private val batteryIgnored = MutableStateFlow(
        AccessibilityChecker.isBatteryOptimizationIgnored(appContext)
    )

    private val overlayGranted = MutableStateFlow(
        AccessibilityChecker.canDrawOverlays(appContext)
    )

    private val deviceAdminActive = MutableStateFlow(
        AccessibilityChecker.isDeviceAdminActive(appContext)
    )

    private val notificationsEnabled = MutableStateFlow(
        AccessibilityChecker.isNotificationsEnabled(appContext)
    )

    val status: StateFlow<SettingStatus> = combine(
        boundAppRepository.observeBoundApps().map { it.size },
        transferAccountRepository.observeAccounts().map { list ->
            list.count { !it.isDefault }
        },
        accessibilityEnabled,
        batteryIgnored,
        overlayGranted,
        deviceAdminActive,
        notificationsEnabled
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingStatus(
            accessibilityEnabled = values[2] as Boolean,
            boundAppCount = values[0] as Int,
            transferAccountCount = values[1] as Int,
            batteryOptimizationIgnored = values[3] as Boolean,
            overlayPermissionGranted = values[4] as Boolean,
            deviceAdminActive = values[5] as Boolean,
            notificationsEnabled = values[6] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingStatus()
    )

    /** Service-alive heartbeat (onServiceConnected has fired). */
    val accessibilityServiceAlive: StateFlow<Boolean> = AntiScamAccessibilityService.isAlive

    /** Decision counters + last event from [ForegroundAppGuard]. */
    val diagnostic: StateFlow<ForegroundAppGuard.Diagnostic> = foregroundAppGuard.diagnostic

    private val _pendingClear = MutableStateFlow(false)
    val pendingClear: StateFlow<Boolean> = _pendingClear.asStateFlow()

    fun refreshAccessibilityStatus() {
        accessibilityEnabled.value = AccessibilityChecker.isOurServiceEnabled(appContext)
        batteryIgnored.value = AccessibilityChecker.isBatteryOptimizationIgnored(appContext)
        overlayGranted.value = AccessibilityChecker.canDrawOverlays(appContext)
        deviceAdminActive.value = AccessibilityChecker.isDeviceAdminActive(appContext)
        notificationsEnabled.value = AccessibilityChecker.isNotificationsEnabled(appContext)
    }

    fun openDeviceAdminSettings() {
        AccessibilityChecker.launchDeviceAdminEnable(appContext)
    }

    fun openNotificationSettings() {
        val intent = AccessibilityChecker.openAppNotificationSettingsIntent(appContext)
        runCatching { appContext.startActivity(intent) }
    }

    fun openAccessibilitySettings() {
        AccessibilityChecker.launchA11ySettings(appContext)
    }

    /**
     * 直接從 Settings 開出 BlockingWarningActivity。讓使用者能驗證警告 UI 本身
     * 是否能正常啟動（與 AccessibilityService 是否能偵測前景無關）。
     */
    fun fireTestWarning() {
        val intent = BlockingWarningActivity.newIntent(
            context = appContext,
            blockedPackage = appContext.packageName,
            blockedLabel = "防詐器（測試）"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { appContext.startActivity(intent) }
    }

    fun requestBatteryExemption() {
        val intent = AccessibilityChecker.requestBatteryOptimizationExemptionIntent(
            appContext.packageName
        )
        runCatching { appContext.startActivity(intent) }
    }

    fun requestOverlayPermission() {
        val intent = AccessibilityChecker.openOverlayPermissionIntent(appContext.packageName)
        runCatching { appContext.startActivity(intent) }
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
            boundAppRepository.clearAll()
            authorizedLaunchTracker.clearAll()
            _pendingClear.value = false
            onDone()
        }
    }

    data class SettingStatus(
        val accessibilityEnabled: Boolean = false,
        val boundAppCount: Int = 0,
        val transferAccountCount: Int = 0,
        val batteryOptimizationIgnored: Boolean = false,
        val overlayPermissionGranted: Boolean = false,
        val deviceAdminActive: Boolean = false,
        val notificationsEnabled: Boolean = false
    )
}
