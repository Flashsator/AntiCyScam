package com.anticyscam.app.ui.tempuse

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
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Single host Activity for the temp-use ("臨時用") forced-calm and lockdown
 * stages — SECOND / THIRD / LOCKED_OUT / BANNED (PRD § 3.3). The mild Stage 1
 * "提醒" is now an in-app dialog in MainFunctionScreen and never reaches this
 * Activity.
 *
 * Stage is read from [com.anticyscam.app.data.prefs.TempUseTracker.snapshot] —
 * the user's tap commits the consume(), which advances the counter and may
 * trigger the post-stage-3 five-minute lockout. The Activity then either
 * launches the target package or shows a terminal lockdown screen.
 *
 * Stage 3 has no "proceed" path — the only action is to dial 165, after which
 * the Activity finishes. Back button is intercepted on Stage 2/3 so the user
 * can't accidentally bail out of the forced calm.
 *
 * The screen is full-screen (immersive) but NOT screen-pinned. The user can
 * only reach a bound app via 防詐器, so escaping this gate gains them nothing:
 * pressing home merely backgrounds it, and the tracker deadlines are
 * wall-clock persisted so re-entry resumes the correct stage. Dropping
 * lockTask removes the "Pin this app?" notification and the fingerprint/PIN
 * prompt that screen-pinning forces on unpin.
 */
@AndroidEntryPoint
class TempUseGateActivity : ComponentActivity() {

    private val viewModel: TempUseGateViewModel by viewModels()

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
                    if (viewModel.canDismiss()) finish()
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
                        val proceeded = viewModel.confirmProceed(this@TempUseGateActivity)
                        if (proceeded) finish()
                    },
                    onCall165 = { dial165() },
                    onCheckBox = viewModel::toggleCheck,
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun returnToHomeAfterLockout() {
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
     * Open the system dialer with 165 pre-filled and finish this Activity so
     * the user is not bounced back. The tracker state (calm timer deadline,
     * count-in-window, lockoutUntil) is already persisted to SharedPreferences,
     * so re-entering the gate from any bound app resumes the correct stage.
     *
     * ACTION_DIAL (not ACTION_CALL) — the user explicitly taps "call" in the
     * system UI; no CALL_PHONE permission prompt is involved.
     */
    private fun dial165() {
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
