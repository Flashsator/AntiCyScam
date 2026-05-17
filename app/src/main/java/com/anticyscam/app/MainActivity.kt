package com.anticyscam.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.anticyscam.app.data.appupdate.AppUpdateChecker
import com.anticyscam.app.data.catalog.CatalogUpdateChecker
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.service.AntiScamForegroundService
import com.anticyscam.app.ui.appupdate.AppUpdateDialog
import com.anticyscam.app.ui.catalog.CatalogUpdateDialog
import com.anticyscam.app.ui.main.MainScreen
import com.anticyscam.app.ui.main.ProtectionStateViewModel
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import com.anticyscam.app.ui.theme.SurfaceBlack
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-Activity host.
 *
 * Behavior change (測試版彈性限制):
 *   - There is no gate. The user can always enter [MainScreen] and use the
 *     防詐器、詐騙專區、設定 tabs directly.
 *   - 無障礙服務 and 通知 are optional protections enabled from the 設定 tab;
 *     they do not gate anything.
 *   - The "防詐器保護中" foreground notification runs once notification
 *     permission is granted **and** at least one App is bound. We drive the
 *     service start/stop from [ProtectionStateViewModel.shouldRunService].
 *     The service also self-checks on each tick so a stale state still
 *     resolves correctly.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val protectionViewModel: ProtectionStateViewModel by viewModels()

    @Inject lateinit var clock: AntiScamClock

    @Inject lateinit var catalogUpdateChecker: CatalogUpdateChecker

    @Inject lateinit var appUpdateChecker: AppUpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cold-start tick — feeds the multi-time cooldown gate so simply
        // moving the system clock forward can't free a fresh account.
        clock.incrementOpenCount()
        // 不在啟動時請求通知權限 —— 通知為選用項目，改由「設定」頁的
        // 「通知（選用）」按鈕在使用者主動點擊時才彈系統對話框。
        catalogUpdateChecker.maybeCheck()
        appUpdateChecker.maybeCheck()
        enableEdgeToEdge()
        setContent {
            AntiCyScamTheme {
                val shouldRunService by protectionViewModel.shouldRunService.collectAsState()
                val catalogState by catalogUpdateChecker.state.collectAsState()
                val appUpdateState by appUpdateChecker.state.collectAsState()
                // Start/stop the foreground service from the observed state.
                // The "防詐器保護中" notification runs once notification
                // permission is granted and at least one App is bound.
                LaunchedEffect(shouldRunService) {
                    if (shouldRunService) {
                        AntiScamForegroundService.start(applicationContext)
                    } else {
                        stopService(
                            Intent(applicationContext, AntiScamForegroundService::class.java)
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SurfaceBlack
                ) {
                    MainScreen()
                }
                // App-binary update takes priority: a stale binary may not
                // even render the catalog dialog correctly. Only surface the
                // catalog prompt once the app-update flow is idle.
                AppUpdateDialog(
                    state = appUpdateState,
                    onAccept = { appUpdateChecker.accept() },
                    onInstall = { appUpdateChecker.install() },
                    onDismiss = { versionCode -> appUpdateChecker.dismiss(versionCode) },
                    onClose = { appUpdateChecker.clearTerminalState() }
                )
                if (appUpdateState is AppUpdateChecker.State.Idle) {
                    CatalogUpdateDialog(
                        state = catalogState,
                        onAccept = { catalogUpdateChecker.accept() },
                        onDismiss = { version -> catalogUpdateChecker.dismiss(version) },
                        onClose = { catalogUpdateChecker.clearTerminalState() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        protectionViewModel.refresh()
    }
}
