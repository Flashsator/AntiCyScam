package com.anticyscam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.anticyscam.app.ui.gate.AccessibilityGateScreen
import com.anticyscam.app.ui.gate.AccessibilityGateViewModel
import com.anticyscam.app.ui.main.MainScreen
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import com.anticyscam.app.ui.theme.SurfaceBlack
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity host. Until the accessibility service is enabled the gate
 * screen is shown and nothing else is reachable — onResume re-checks the
 * system state every time the user returns from Settings, automatically
 * passing the gate when our service flips on.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val gateViewModel: AccessibilityGateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AntiCyScamTheme {
                val state by gateViewModel.state.collectAsState()
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceBlack),
                    color = SurfaceBlack
                ) {
                    if (state.isServiceEnabled) {
                        MainScreen()
                    } else {
                        AccessibilityGateScreen(
                            isEnabled = state.isServiceEnabled,
                            onOpenSettings = { /* re-check happens in onResume */ }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gateViewModel.refresh()
    }
}
