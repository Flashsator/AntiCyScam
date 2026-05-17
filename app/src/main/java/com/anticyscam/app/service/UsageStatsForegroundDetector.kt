package com.anticyscam.app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.anticyscam.app.MainActivity
import com.anticyscam.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * a11y-OFF 後備前景偵測（需求 #2 的折衷方案 —— 使用者選 Q1 = UsageStats）。
 *
 * 無障礙服務未啟用時，Android 沒有任何「即時」回呼能得知別的 App 被帶到
 * 前景；唯一的替代是 [UsageStatsManager]（需 PACKAGE_USAGE_STATS 特殊權限）。
 * 本類別以約 1 秒輪詢最近的前景事件，命中已綁定 App 時用
 * `TYPE_APPLICATION_OVERLAY`（需 SYSTEM_ALERT_WINDOW）蓋上同一張全螢幕警告。
 *
 * 與 [AntiScamAccessibilityService] **互斥**：僅在 a11y 關閉時由
 * [AntiScamForegroundService] 啟動 —— 兩者共用 [ForegroundAppGuard]，同時跑
 * 會重複跳警告。輪詢有 ~1s 延遲，比 a11y 事件略慢。
 */
class UsageStatsForegroundDetector(
    private val context: Context,
    private val guard: ForegroundAppGuard
) {

    private var pollJob: Job? = null

    @Volatile
    private var overlayView: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Idempotent — a second call while already polling is a no-op. */
    fun start(scope: CoroutineScope) {
        if (pollJob?.isActive == true) return
        Log.i(TAG, "UsageStats fallback detector started")
        pollJob = scope.launch {
            while (isActive) {
                runCatching { pollOnce() }
                    .onFailure { Log.w(TAG, "poll tick failed", it) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        if (pollJob == null) return
        Log.i(TAG, "UsageStats fallback detector stopped")
        pollJob?.cancel()
        pollJob = null
        removeOverlay()
    }

    private fun pollOnce() {
        val pkg = latestForegroundPackage() ?: return
        if (pkg == context.packageName) return
        when (val decision = guard.evaluate(foregroundPkg = pkg, ownPkg = context.packageName)) {
            ForegroundAppGuard.Decision.Ignore -> Unit
            is ForegroundAppGuard.Decision.AllowAuthorized -> removeOverlay()
            is ForegroundAppGuard.Decision.BlockUnauthorized -> showOverlay(decision.label)
        }
    }

    /**
     * Most-recent foreground package from [UsageStatsManager.queryEvents].
     * The query window is intentionally wide so that, after the user
     * dismisses the overlay (which calls [ForegroundAppGuard.resetLastObserved]),
     * an idle bank app still resolves — its MOVE_TO_FOREGROUND event from a
     * few seconds earlier is still inside the window, so the block re-pops.
     */
    private fun latestForegroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - QUERY_WINDOW_MS, now)
        val event = UsageEvents.Event()
        var latestPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                latestPkg = event.packageName
            }
        }
        return latestPkg
    }

    private fun showOverlay(blockedLabel: String) {
        if (overlayView != null) return
        mainHandler.post {
            if (overlayView != null) return@post
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@post
            val root = LayoutInflater.from(context)
                .inflate(R.layout.overlay_blocking_warning, null)

            val labelView = root.findViewById<TextView>(R.id.overlay_blocked_label)
            if (blockedLabel.isNotBlank()) {
                labelView.text =
                    context.getString(R.string.warning_blocked_label_format, blockedLabel)
                labelView.visibility = View.VISIBLE
            }
            root.findViewById<Button>(R.id.overlay_dismiss).setOnClickListener {
                removeOverlay()
                // Clear the dedup gate so the next poll re-evaluates the
                // still-foreground bank app and re-pops the warning.
                guard.resetLastObserved()
                // Fired from a user tap → counts as user activation, so the
                // Android 12+ Background Activity Start restriction does not
                // apply and MainActivity reliably comes to the front.
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                runCatching { context.startActivity(mainIntent) }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE
            )
            runCatching { wm.addView(root, params) }
                .onSuccess { overlayView = root }
                .onFailure { Log.e(TAG, "Failed to add fallback warning overlay", it) }
        }
    }

    private fun removeOverlay() {
        mainHandler.post {
            val view = overlayView ?: return@post
            overlayView = null
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@post
            runCatching { wm.removeView(view) }
        }
    }

    private companion object {
        const val TAG = "UsageStatsDetector"
        const val POLL_INTERVAL_MS = 1_000L
        const val QUERY_WINDOW_MS = 10_000L
    }
}
