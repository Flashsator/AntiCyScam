package com.anticyscam.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.utils.AccessibilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drives the "防詐器保護中" foreground service from [com.anticyscam.app.MainActivity].
 *
 * 測試版彈性限制（需求 1、4）：防詐器不再有強制解鎖閘。常駐通知（前景服務）
 * 只在「通知權限已授權 **且** 已綁定至少一個 App」時才出現 —— 未綁定 App
 * 之前顯示通知沒有意義（沒有保護目標），因此延後到第一個 App 綁定後才啟動。
 *
 * 通知權限沒有系統 callback，必須在 `onResume` 呼叫 [refresh] 重新讀取。
 */
@HiltViewModel
class ProtectionStateViewModel @Inject constructor(
    application: Application,
    boundAppRepository: BoundAppRepository
) : AndroidViewModel(application) {

    private val notificationsEnabled = MutableStateFlow(
        AccessibilityChecker.isNotificationsEnabled(application)
    )

    /** True when the foreground service + its persistent notification should run. */
    val shouldRunService: StateFlow<Boolean> = combine(
        notificationsEnabled,
        boundAppRepository.observeBoundApps().map { it.isNotEmpty() }
    ) { notify, hasBoundApp ->
        notify && hasBoundApp
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    fun refresh() {
        notificationsEnabled.value =
            AccessibilityChecker.isNotificationsEnabled(getApplication())
    }
}
