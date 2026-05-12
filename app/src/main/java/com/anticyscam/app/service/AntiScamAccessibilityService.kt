package com.anticyscam.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.anticyscam.app.ui.warning.BlockingWarningActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground-app watcher.
 *
 * Lifecycle:
 *   - Enabled by the user in System Settings → Accessibility.
 *   - On enable, [onServiceConnected] fires; we wire up [ForegroundAppGuard].
 *   - Every WINDOW_STATE_CHANGED event surfaces a candidate foreground
 *     package. We route it to the guard, which returns one of:
 *       Ignore | AllowAuthorized | BlockUnauthorized
 *   - BlockUnauthorized → trigger the warning + force pull-back.
 *
 * Why `@AndroidEntryPoint`: AccessibilityService extends Service, and Hilt
 * supports Service-level injection out of the box. This lets us share the
 * same singleton dependencies (BoundAppRepository, AuthorizedLaunchTracker)
 * with the UI side without manual wiring.
 */
@AndroidEntryPoint
class AntiScamAccessibilityService : AccessibilityService() {

    @Inject lateinit var guard: ForegroundAppGuard

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AntiScamAccessibilityService connected")
        guard.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore the obvious system-UI noise; system_ui and launcher
        // bounce between every transition. The guard also filters but
        // short-circuit here for cheap rejection.
        if (pkg in IGNORED_PACKAGES) return

        when (val decision = guard.evaluate(foregroundPkg = pkg, ownPkg = packageName)) {
            ForegroundAppGuard.Decision.Ignore -> Unit
            is ForegroundAppGuard.Decision.AllowAuthorized -> {
                Log.d(TAG, "Authorized launch: ${decision.packageName}")
            }
            is ForegroundAppGuard.Decision.BlockUnauthorized -> {
                Log.w(TAG, "BLOCKED unauthorized launch: ${decision.packageName}")
                forcePullBack(decision.packageName, decision.label)
            }
        }
    }

    override fun onInterrupt() {
        // Required override; no-op for window-state-only listener.
    }

    /**
     * Two-step force-pull-back:
     *   1. GLOBAL_ACTION_HOME — yank the user off the offending app.
     *   2. Start the fullscreen [BlockingWarningActivity] in a new task
     *      so it lands above whatever the launcher shows next.
     *
     * Performed in this order so even if the warning Activity start fails
     * (e.g. background-launch restriction kicked in), the user is at
     * least no longer on the unauthorized banking screen.
     */
    private fun forcePullBack(packageName: String, label: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = BlockingWarningActivity.newIntent(
            context = this,
            blockedPackage = packageName,
            blockedLabel = label
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            )
        }
        startActivity(intent)
    }

    private companion object {
        const val TAG = "AntiScamA11y"
        val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            ""
        )
    }
}
