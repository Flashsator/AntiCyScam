package com.anticyscam.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.repository.BoundAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drives the "防詐器保護中" foreground service from [com.anticyscam.app.MainActivity].
 *
 * 服務是偵測引擎（[com.anticyscam.app.service.UsageStatsForegroundDetector]）的
 * 宿主，只要「已綁定至少一個 App」就該運作 —— 與服務自身的
 * `AntiScamForegroundService.enforceProtectionState()` 一致。
 *
 * 通知權限是**選用**項目：它只影響常駐通知是否「顯示」（未授權時 OS 自行隱藏），
 * 不作為服務／偵測的啟動條件。偵測真正需要的是「使用情況存取權 + 上層顯示」，
 * 由服務內的 `updateDetectionMode()` 動態判斷，不在此處把關 —— 因此使用者只要
 * 開這兩項並綁定 App，保護即生效，毋須一併開啟通知。
 */
@HiltViewModel
class ProtectionStateViewModel @Inject constructor(
    application: Application,
    boundAppRepository: BoundAppRepository
) : AndroidViewModel(application) {

    /**
     * True when the foreground service should run — i.e. at least one App is
     * bound. Notification permission is intentionally NOT part of this: the
     * service must keep detecting even when the persistent notification is
     * hidden because the user declined POST_NOTIFICATIONS.
     */
    val shouldRunService: StateFlow<Boolean> =
        boundAppRepository.observeBoundApps()
            .map { it.isNotEmpty() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false
            )
}
