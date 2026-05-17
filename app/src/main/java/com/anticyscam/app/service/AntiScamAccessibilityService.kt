package com.anticyscam.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.anticyscam.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *   - BlockUnauthorized → show the fullscreen overlay warning.
 *
 * Why an overlay (Phase H): Android 12+ Background Activity Start (BAL)
 * restrictions silently swallow `startActivity()` calls made from a service
 * on many OEM builds, which is why launching `BlockingWarningActivity`
 * directly from the service did not actually surface a warning when the
 * user tapped a bound app from the launcher. `TYPE_ACCESSIBILITY_OVERLAY`
 * does not require an Activity start at all — AccessibilityServices may
 * add overlay windows without `SYSTEM_ALERT_WINDOW` permission and
 * without going through Activity-task management, so BAL never applies.
 */
@AndroidEntryPoint
class AntiScamAccessibilityService : AccessibilityService() {

    @Inject lateinit var guard: ForegroundAppGuard

    @Volatile
    private var overlayView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AntiScamAccessibilityService connected")
        _isAlive.value = true
        guard.start()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "AntiScamAccessibilityService unbinding — OS revoked or user toggled off")
        _isAlive.value = false
        removeOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isAlive.value = false
        removeOverlay()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> Unit
            else -> return
        }
        val pkg = event.packageName?.toString() ?: return

        // Ignore the obvious system-UI noise; system_ui and launcher
        // bounce between every transition. The guard also filters but
        // short-circuit here for cheap rejection.
        if (pkg in IGNORED_PACKAGES) return

        // Our own UI is now foreground — but per Q1, the overlay does NOT
        // auto-yank to home; it sits directly over the bound app. Only the
        // explicit dismiss tap removes the overlay. Do NOT removeOverlay()
        // here, otherwise opening 防詐器 to re-enter via the proper flow
        // would silently clear the block.
        if (pkg == packageName) return

        when (val decision = guard.evaluate(foregroundPkg = pkg, ownPkg = packageName)) {
            ForegroundAppGuard.Decision.Ignore -> Unit
            is ForegroundAppGuard.Decision.AllowAuthorized -> {
                Log.d(TAG, "Authorized launch: ${decision.packageName}")
                removeOverlay()
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
     * Render a fullscreen TYPE_ACCESSIBILITY_OVERLAY directly on top of the
     * offending bound app. Per Q1, we deliberately do NOT yank to home
     * first — the overlay pins in place over the bank/payment UI so the
     * user is not bounced to launcher on every unauthorized foreground
     * event. The overlay survives BAL because it is added directly to the
     * WindowManager from inside the AccessibilityService — no Activity
     * start is involved.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun forcePullBack(packageName: String, label: String) {
        showOverlay(label)
    }

    private fun showOverlay(blockedLabel: String) {
        if (overlayView != null) return // already on screen
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        val inflater = LayoutInflater.from(this)
        val root = inflater.inflate(R.layout.overlay_blocking_warning, null)

        val labelView = root.findViewById<TextView>(R.id.overlay_blocked_label)
        if (blockedLabel.isNotBlank()) {
            labelView.text = getString(R.string.warning_blocked_label_format, blockedLabel)
            labelView.visibility = View.VISIBLE
        }
        root.findViewById<Button>(R.id.overlay_dismiss).setOnClickListener {
            removeOverlay()
            // Clear the dedup gate so the next foreground event for the
            // same bound app re-triggers the warning. Without this, the
            // user tapping the same bound app twice from launcher silently
            // bypasses the block (same-package back-to-back returns Ignore).
            guard.resetLastObserved()
            // 需求 #2：使用者只要進不去網銀 App 就好 —— 警告必須「關不掉、
            // 持續重跳」。removeOverlay 單獨用會把使用者留在網銀 App 前景，
            // 改開 MainActivity 在部分 OEM 上仍可被 back 鍵退回網銀。
            // performGlobalAction(GLOBAL_ACTION_HOME) 是唯一能在所有 OEM 上
            // 確實把網銀 App 踢出前景的方式；使用者之後若再點網銀，會觸發
            // 新的 WINDOW_STATE_CHANGED → guard 再次 Block → 警告重跳。
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // FLAG_NOT_FOCUSABLE keeps the soft keyboard from popping;
            // FLAG_LAYOUT_IN_SCREEN draws over status / nav bars.
            // (FLAG_LAYOUT_INSET_DECOR 在 API 30+ 已棄用：window decoration
            // 會自動依 insets 調整，不需要再請求。)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )

        runCatching { wm.addView(root, params) }
            .onSuccess { overlayView = root }
            .onFailure { Log.e(TAG, "Failed to add warning overlay", it) }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        runCatching { wm.removeView(view) }
    }

    companion object {
        private const val TAG = "AntiScamA11y"
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            ""
        )

        /**
         * 觀察服務是否真的在跑（onServiceConnected 已觸發、onUnbind/onDestroy 尚未觸發）。
         * 與 Settings.Secure 的「啟用清單」狀態不同 — 系統可能顯示已啟用但實際 callback
         * 沒接上。Settings 頁的 Diagnostic 卡會合併兩個訊號顯示。
         */
        private val _isAlive = MutableStateFlow(false)
        val isAlive: StateFlow<Boolean> = _isAlive.asStateFlow()
    }
}
