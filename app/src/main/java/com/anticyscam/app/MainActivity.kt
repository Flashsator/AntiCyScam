package com.anticyscam.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.service.AntiScamForegroundService
import com.anticyscam.app.ui.gate.AccessibilityGateViewModel
import com.anticyscam.app.ui.main.MainScreen
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import com.anticyscam.app.ui.theme.SurfaceBlack
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-Activity host.
 *
 * Behavior change (requirement #3 revised):
 *   - The accessibility/device-admin/notification gate no longer blocks the
 *     whole app. The user can always enter [MainScreen] and use the 詐騙專區
 *     and 設定 tabs.
 *   - The 防詐器 tab itself renders an inline gate inside [MainFunctionScreen]
 *     when the three requirements are not all met.
 *   - The "防詐器保護中" foreground notification is only allowed to appear when
 *     all three requirements hold; we drive the service start/stop from the
 *     observed gate state here. The service also self-checks on each tick so a
 *     stale state still resolves correctly.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val gateViewModel: AccessibilityGateViewModel by viewModels()

    @Inject lateinit var clock: AntiScamClock

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Either outcome is fine — the gate will reflect the new state on
        // the next refresh and re-evaluate the foreground notification.
        gateViewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cold-start tick — feeds the multi-time cooldown gate so simply
        // moving the system clock forward can't free a fresh account.
        clock.incrementOpenCount()
        maybeRequestPostNotificationsPermission()
        enableEdgeToEdge()
        setContent {
            AntiCyScamTheme {
                val state by gateViewModel.state.collectAsState()
                // Start/stop the foreground service from the observed gate
                // state. Requirement #4: only show "防詐器保護中" notification
                // when all three requirements are satisfied.
                LaunchedEffect(state.allRequirementsMet) {
                    if (state.allRequirementsMet) {
                        AntiScamForegroundService.start(applicationContext)
                    } else {
                        stopService(
                            Intent(applicationContext, AntiScamForegroundService::class.java)
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceBlack),
                    color = SurfaceBlack
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gateViewModel.refresh()
    }

    private fun maybeRequestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
