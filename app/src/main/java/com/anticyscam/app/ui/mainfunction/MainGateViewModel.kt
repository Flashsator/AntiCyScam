package com.anticyscam.app.ui.mainfunction

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.anticyscam.app.utils.AccessibilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 防詐器主頁的權限閘門。
 *
 * 主頁功能（轉帳帳號 / 綁定 App）以「使用情況存取權」與「上層顯示」兩項
 * 特殊權限作為使用前提：缺任一項，[com.anticyscam.app.service.UsageStatsForegroundDetector]
 * 後備偵測就無法偵測前景 App 或蓋出警告，防詐器形同虛設。因此未開齊兩項時
 * [com.anticyscam.app.ui.mainfunction.MainFunctionScreen] 改顯示閘門畫面，
 * 不給使用。
 *
 * 兩項皆為特殊存取權，沒有系統 callback，必須在 `onResume` 呼叫 [refresh]
 * 重新讀取授權狀態。
 */
@HiltViewModel
class MainGateViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    data class GateState(
        val usageStatsGranted: Boolean = false,
        val overlayGranted: Boolean = false
    ) {
        /** 兩項權限都到位才放行主頁功能。 */
        val allGranted: Boolean get() = usageStatsGranted && overlayGranted
    }

    private val _gateState = MutableStateFlow(readState())
    val gateState: StateFlow<GateState> = _gateState.asStateFlow()

    /** Re-read both special-access permissions; call from the screen's onResume. */
    fun refresh() {
        _gateState.value = readState()
    }

    private fun readState(): GateState {
        val ctx = getApplication<Application>()
        return GateState(
            usageStatsGranted = AccessibilityChecker.hasUsageStatsPermission(ctx),
            overlayGranted = AccessibilityChecker.canDrawOverlays(ctx)
        )
    }

    fun openUsageAccessSettings() {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.startActivity(
                AccessibilityChecker.openUsageAccessSettingsIntent(ctx.packageName)
            )
        }
    }

    fun openOverlaySettings() {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.startActivity(
                AccessibilityChecker.openOverlayPermissionIntent(ctx.packageName)
            )
        }
    }
}
