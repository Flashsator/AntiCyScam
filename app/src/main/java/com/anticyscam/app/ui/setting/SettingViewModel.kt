package com.anticyscam.app.ui.setting

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.catalog.CatalogUpdateChecker
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.data.repository.ScamInfoRepository
import com.anticyscam.app.data.repository.TransferAccountRepository
import com.anticyscam.app.service.ForegroundAppGuard
import com.anticyscam.app.ui.warning.BlockingWarningActivity
import com.anticyscam.app.utils.SystemAccessChecker
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
 * [status] is a derived StateFlow combining bound-app / transfer-account counts
 * with the live special-permission states (battery whitelist, overlay,
 * usage-access, device admin, notifications). None of those permissions emit
 * a system callback when the user toggles them in system settings, so
 * [refreshStatus] re-reads them on resume.
 */
@HiltViewModel
class SettingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val boundAppRepository: BoundAppRepository,
    private val transferAccountRepository: TransferAccountRepository,
    private val foregroundAppGuard: ForegroundAppGuard,
    private val scamInfoRepository: ScamInfoRepository,
    private val catalogUpdateChecker: CatalogUpdateChecker
) : ViewModel() {

    private val batteryIgnored = MutableStateFlow(
        SystemAccessChecker.isBatteryOptimizationIgnored(appContext)
    )

    private val overlayGranted = MutableStateFlow(
        SystemAccessChecker.canDrawOverlays(appContext)
    )

    private val deviceAdminActive = MutableStateFlow(
        SystemAccessChecker.isDeviceAdminActive(appContext)
    )

    private val notificationsEnabled = MutableStateFlow(
        SystemAccessChecker.isNotificationsEnabled(appContext)
    )

    private val usageStatsGranted = MutableStateFlow(
        SystemAccessChecker.hasUsageStatsPermission(appContext)
    )

    val status: StateFlow<SettingStatus> = combine(
        boundAppRepository.observeBoundApps().map { it.size },
        transferAccountRepository.observeAccounts().map { list ->
            list.count { !it.isDefault }
        },
        batteryIgnored,
        overlayGranted,
        deviceAdminActive,
        notificationsEnabled,
        usageStatsGranted
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingStatus(
            boundAppCount = values[0] as Int,
            transferAccountCount = values[1] as Int,
            batteryOptimizationIgnored = values[2] as Boolean,
            overlayPermissionGranted = values[3] as Boolean,
            deviceAdminActive = values[4] as Boolean,
            notificationsEnabled = values[5] as Boolean,
            usageStatsGranted = values[6] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingStatus()
    )

    /** Decision counters + last event from [ForegroundAppGuard]. */
    val diagnostic: StateFlow<ForegroundAppGuard.Diagnostic> = foregroundAppGuard.diagnostic

    private val _catalogMeta = MutableStateFlow(CatalogMeta())
    val catalogMeta: StateFlow<CatalogMeta> = _catalogMeta.asStateFlow()

    init {
        // 訂閱 repository.catalog — CatalogUpdateChecker 套用新版本後
        // _catalogMeta 會自動更新，設定頁顯示的版本/日期就不會卡在舊值。
        viewModelScope.launch {
            scamInfoRepository.catalog.collect { catalog ->
                if (catalog == null) {
                    runCatching { scamInfoRepository.load() }
                    return@collect
                }
                _catalogMeta.value = CatalogMeta(
                    version = catalog.version,
                    displayVersion = catalog.displayVersion.ifEmpty { "v${catalog.version}" },
                    lastUpdated = catalog.lastUpdated,
                    sources = OFFICIAL_SOURCES
                )
            }
        }
    }

    /** Re-read every special-permission state; call from the screen's onResume. */
    fun refreshStatus() {
        batteryIgnored.value = SystemAccessChecker.isBatteryOptimizationIgnored(appContext)
        overlayGranted.value = SystemAccessChecker.canDrawOverlays(appContext)
        deviceAdminActive.value = SystemAccessChecker.isDeviceAdminActive(appContext)
        notificationsEnabled.value = SystemAccessChecker.isNotificationsEnabled(appContext)
        usageStatsGranted.value = SystemAccessChecker.hasUsageStatsPermission(appContext)
    }

    fun openDeviceAdminSettings() {
        SystemAccessChecker.launchDeviceAdminEnable(appContext)
    }

    fun openNotificationSettings() {
        val intent = SystemAccessChecker.openAppNotificationSettingsIntent(appContext)
        runCatching { appContext.startActivity(intent) }
    }

    /**
     * 開啟「使用情況存取權」設定頁。[UsageStatsForegroundDetector] 前景偵測
     * 需要此特殊權限才能輪詢前景 App。
     */
    fun openUsageAccessSettings() {
        val intent = SystemAccessChecker.openUsageAccessSettingsIntent(appContext.packageName)
        runCatching { appContext.startActivity(intent) }
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
        val intent = SystemAccessChecker.requestBatteryOptimizationExemptionIntent(
            appContext.packageName
        )
        runCatching { appContext.startActivity(intent) }
    }

    fun requestOverlayPermission() {
        val intent = SystemAccessChecker.openOverlayPermissionIntent(appContext.packageName)
        runCatching { appContext.startActivity(intent) }
    }

    /**
     * Debug-only. Wipes catalog-update DataStore + downloaded override file
     * and immediately re-runs the check, so QA can re-trigger the update
     * dialog without uninstalling the APK or waiting 24h.
     */
    fun resetCatalogUpdateStateForDebug() {
        catalogUpdateChecker.resetForDebug()
    }

    data class SettingStatus(
        val boundAppCount: Int = 0,
        val transferAccountCount: Int = 0,
        val batteryOptimizationIgnored: Boolean = false,
        val overlayPermissionGranted: Boolean = false,
        val deviceAdminActive: Boolean = false,
        val notificationsEnabled: Boolean = false,
        val usageStatsGranted: Boolean = false
    )

    data class CatalogMeta(
        val version: Int = 0,
        val displayVersion: String = "",
        val lastUpdated: String = "",
        val sources: List<OfficialSource> = emptyList()
    )

    data class OfficialSource(val label: String, val url: String)

    private companion object {
        // 寫死的官方來源清單，避免日後 JSON 被誤改混入無法驗證的非官方項目。
        // 維運若要新增來源，必須先確認對方為政府機關／官方專線並修改這份清單。
        val OFFICIAL_SOURCES = listOf(
            OfficialSource(
                label = "165 反詐騙諮詢專線（內政部警政署）",
                url = "https://165.npa.gov.tw/"
            ),
            OfficialSource(
                label = "內政部警政署 刑事警察局",
                url = "https://www.cib.npa.gov.tw/"
            ),
            OfficialSource(
                label = "行政院 打詐國家隊",
                url = "https://www.ey.gov.tw/Page/DAD883AAD1555692"
            ),
            OfficialSource(
                label = "數位發展部",
                url = "https://moda.gov.tw/"
            ),
            OfficialSource(
                label = "金融監督管理委員會",
                url = "https://www.fsc.gov.tw/"
            ),
            OfficialSource(
                label = "法務部 調查局",
                url = "https://www.mjib.gov.tw/"
            ),
            OfficialSource(
                label = "NCC 堵詐專區（電信網路面）",
                url = "https://www.ncc.gov.tw/chncc/app/data/list?id=395"
            )
        )
    }
}
