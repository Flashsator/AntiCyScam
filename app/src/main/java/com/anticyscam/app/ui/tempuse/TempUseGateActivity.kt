package com.anticyscam.app.ui.tempuse

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.anticyscam.app.MainActivity
import com.anticyscam.app.data.prefs.TempUseTracker
import com.anticyscam.app.service.AntiScamDeviceAdminReceiver
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Single host Activity for the three "臨時用" warning stages (PRD § 3.3).
 *
 * Stage is read from [com.anticyscam.app.data.prefs.TempUseTracker.snapshot] —
 * the user's tap commits the consume(), which advances the counter and may
 * trigger the post-stage-3 five-minute lockout. The Activity then either
 * launches the target package or shows a terminal lockdown screen.
 *
 * Stage 3 has no "proceed" path — the only action is to dial 165, after which
 * the Activity finishes. Back button is intercepted on Stage 2/3 so the user
 * can't accidentally bail out of the forced calm.
 */
@AndroidEntryPoint
class TempUseGateActivity : ComponentActivity() {

    private val viewModel: TempUseGateViewModel by viewModels()

    // Phase F: only request screen-pin once per Activity lifetime so the system
    // "Pin this app?" prompt does not re-fire on every onResume cycle.
    private var lockTaskStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        val accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L)
        val accountNumber = intent.getStringExtra(EXTRA_ACCOUNT_NUMBER).orEmpty()
        viewModel.bootstrap(
            targetPackage = targetPackage,
            accountId = if (accountId > 0) accountId else null,
            accountNumber = accountNumber
        )

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.canDismiss()) finishGate()
                }
            }
        )

        // Plan v4 Item 7: when the Stage 3 / LOCKED_OUT countdown reaches
        // zero, auto-finish and pull MainActivity back to the front so the
        // user is never left staring at a 0:00 timer.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.returnToHome.collect {
                    returnToHomeAfterLockout()
                }
            }
        }

        setContent {
            AntiCyScamTheme {
                val state by viewModel.state.collectAsState()
                TempUseGateScreen(
                    state = state,
                    onProceed = {
                        // Phase P: non-DO lockTask silently drops cross-package
                        // startActivity, so AppLauncher's launch is a no-op
                        // while the pin is active. Release the pin BEFORE
                        // confirmProceed when we know the proceed path will
                        // actually fire a launch — otherwise the bound app is
                        // never foregrounded and the user is stuck on the gate.
                        val willLaunch = when (state.stage) {
                            TempUseTracker.Stage.FIRST -> true
                            TempUseTracker.Stage.SECOND -> state.canProceedStage2
                            TempUseTracker.Stage.THIRD,
                            TempUseTracker.Stage.LOCKED_OUT -> false
                        }
                        if (willLaunch) {
                            runCatching { stopLockTask() }
                            lockTaskStarted = false
                        }
                        val proceeded = viewModel.confirmProceed(this@TempUseGateActivity)
                        if (proceeded) finish()
                    },
                    onCall165 = { dial165() },
                    onCheckBox = viewModel::toggleCheck,
                    onDismiss = { finishGate() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!lockTaskStarted) startKioskOrPin()
    }

    /**
     * Plan v4 Item 8 (revised Q3=c hybrid). If the app has somehow been
     * promoted to Device Owner via ADB (`dpm set-device-owner …`), upgrade
     * the lockTask path to a true allow-listed kiosk by registering this
     * package via `setLockTaskPackages` before calling `startLockTask`.
     * Otherwise fall through to the existing non-DO screen-pin behavior.
     *
     * We never SET Device Owner from inside the app — that has to be done
     * manually pre-account-setup by the user via adb. Detection only.
     */
    private fun startKioskOrPin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isOwner = dpm?.isDeviceOwnerApp(packageName) == true
        if (isOwner && dpm != null) {
            runCatching {
                dpm.setLockTaskPackages(
                    ComponentName(this, AntiScamDeviceAdminReceiver::class.java),
                    arrayOf(packageName)
                )
            }
        }
        runCatching { startLockTask() }.onSuccess { lockTaskStarted = true }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun finishGate() {
        runCatching { stopLockTask() }
        lockTaskStarted = false
        finish()
    }

    private fun returnToHomeAfterLockout() {
        runCatching { stopLockTask() }
        lockTaskStarted = false
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(mainIntent)
        finish()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Plan v4 follow-up: open the system dialer with 165 pre-filled and
     * finish this Activity so the user is not bounced back.
     *
     * Why finish(): without it, the Gate Activity stays alive in its task
     * and the system re-fronts us whenever the dialer briefly yields focus
     * (keypad transitions, end-of-call), which re-runs onResume →
     * startLockTask → "Pin this app?" prompt loops on top of the dialer.
     * Tearing the Activity down breaks that loop. The tracker state
     * (calm timer deadline, count-in-window, lockoutUntil) is already
     * persisted to SharedPreferences, so re-entering the gate from any
     * bound app will resume the correct stage.
     *
     * ACTION_DIAL (not ACTION_CALL) — user explicitly taps "call" in the
     * system UI; no CALL_PHONE permission prompt is involved.
     */
    private fun dial165() {
        runCatching { stopLockTask() }
        lockTaskStarted = false
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:165")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
        finish()
    }

    companion object {
        private const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        private const val EXTRA_ACCOUNT_ID = "extra_account_id"
        private const val EXTRA_ACCOUNT_NUMBER = "extra_account_number"

        fun newIntent(
            context: Context,
            targetPackage: String?,
            accountId: Long?,
            accountNumber: String
        ): Intent = Intent(context, TempUseGateActivity::class.java).apply {
            putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
            accountId?.let { putExtra(EXTRA_ACCOUNT_ID, it) }
            putExtra(EXTRA_ACCOUNT_NUMBER, accountNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
