package com.anticyscam.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.anticyscam.app.MainActivity
import com.anticyscam.app.R
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.utils.AccessibilityChecker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sticky foreground service that keeps the app process alive on aggressive
 * OEMs (Xiaomi, Oppo, Huawei) so the [AntiScamAccessibilityService] does not
 * get silently killed by background-app reaper. A persistent notification is
 * required by Android — we use it to surface the protection status as well.
 *
 * Lifecycle:
 *  - Started by [MainActivity.onCreate] and by [BootReceiver] after device
 *    boot, so the watchdog is online before the user opens any bank app.
 *  - START_STICKY so the OS attempts a restart if killed.
 */
@AndroidEntryPoint
class AntiScamForegroundService : Service() {

    @Inject lateinit var boundAppRepository: BoundAppRepository
    @Inject lateinit var clock: AntiScamClock
    @Inject lateinit var foregroundAppGuard: ForegroundAppGuard

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchdogJob: Job? = null
    // 已綁定 App 數的快取 — 由 onCreate 啟動的 bound-app collector 持續更新，
    // 供 [enforceProtectionState] 在 watchdog tick 同步讀取。
    @Volatile private var boundAppCount = 0
    // 通話結束自動偵測 → 掃 OEM 錄音檔 → 通知使用者送進辨識流程
    private val callRecordingDetector by lazy { CallRecordingDetector(this) }
    // a11y-OFF 後備前景偵測（UsageStats 輪詢）。與無障礙服務互斥，由
    // [updateDetectionMode] 依無障礙服務開關狀態啟動／停止。
    private val usageStatsDetector by lazy {
        UsageStatsForegroundDetector(this, foregroundAppGuard)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // 啟動瞬間先以低重要性 placeholder 通知滿足 startForeground 義務；真正
        // 的守門交給下方 bound-app collector —— 等第一次 DB emission 帶回真實
        // 綁定數後才決定要不要 stopSelf，避免「冷啟動時 count 仍是 0」的誤判。
        startForegroundCompat()
        callRecordingDetector.start()
        // 確保前景判決核心的綁定 App 快照被填入 —— a11y 關閉時無障礙服務不會
        // 呼叫 start()，UsageStats 後備偵測就會拿到空快照。start() 為冪等。
        foregroundAppGuard.start()
        observeBoundAppsAndEnforce()
        // Safety-net settle: BootReceiver also does this, but the service is
        // also restarted by Android after process death without a reboot —
        // catching that path is why we settle here too. Cheap when no rows.
        scope.launch {
            runCatching { boundAppRepository.settleAll(clock.snapshot()) }
                .onFailure { Log.w(TAG, "onCreate settleAll failed", it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        // 守門與 watchdog 由 onCreate 啟動的 bound-app collector 負責；redeliver
        // 時 collector 仍存活，此處只需重新宣告前景狀態。
        startForegroundCompat()
        return START_STICKY
    }

    /**
     * 訂閱已綁定 App 清單。每次變動都更新 [boundAppCount] 並重跑守門檢查 ——
     * 綁定數歸零（使用者解除最後一個 App）時 [enforceProtectionState] 會
     * stopSelf，落實「未綁定 App 不顯示通知」（需求 #4）。
     */
    private fun observeBoundAppsAndEnforce() {
        scope.launch {
            boundAppRepository.observeBoundApps().collect { apps ->
                boundAppCount = apps.size
                if (!enforceProtectionState()) return@collect
                startWatchdog()
                updateDetectionMode()
                refreshNotificationFromStatus()
            }
        }
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        callRecordingDetector.destroy()
        usageStatsDetector.stop()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 依無障礙服務開關狀態切換前景偵測模式（需求 #2、#5）：
     *  - a11y 開啟 → [AntiScamAccessibilityService] 以即時事件攔截，UsageStats
     *    後備偵測必須停掉，否則兩者共用 [ForegroundAppGuard]、會重複跳警告。
     *  - a11y 關閉 → 沒有即時回呼，啟動 [UsageStatsForegroundDetector] 輪詢，
     *    但前提是 PACKAGE_USAGE_STATS 與懸浮窗權限都已授權；缺一不可就停掉。
     * 冪等：detector 的 start／stop 重複呼叫皆為 no-op，可安全於 watchdog 反覆呼叫。
     */
    private fun updateDetectionMode() {
        val a11yOn = AccessibilityChecker.isOurServiceEnabled(this)
        val canFallback = !a11yOn &&
            AccessibilityChecker.hasUsageStatsPermission(this) &&
            AccessibilityChecker.canDrawOverlays(this)
        if (canFallback) {
            usageStatsDetector.start(scope)
        } else {
            usageStatsDetector.stop()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_service_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.foreground_service_text)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(currentStatusText())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * 週期性檢查無障礙服務是否仍存活 + 電池白名單狀態。任一不符就把通知文字
     * 換成警告版本，給使用者立即可見的回饋。Phase 3 Android 12 可靠性修補。
     */
    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                runCatching {
                    if (!enforceProtectionState()) return@launch
                    // a11y 服務可能在執行期間被使用者開啟／關閉 —— 每個 tick
                    // 重新評估，必要時切換 UsageStats 後備偵測的啟停。
                    updateDetectionMode()
                    refreshNotificationFromStatus()
                    // 處理使用者後來給/收回 READ_PHONE_STATE / READ_MEDIA_AUDIO 的情況
                    callRecordingDetector.refreshIfNeeded()
                }.onFailure { Log.w(TAG, "watchdog tick failed", it) }
            }
        }
    }

    /**
     * 守門檢查 — 需求 #3、#4：常駐通知「防詐器保護中」只在「通知權限已授權」
     * 且「已綁定至少一個 App」時存在。未綁定 App 時顯示通知沒有保護目標，
     * 因此 stopSelf。無障礙服務／裝置管理員為選用的進階保護，不再作為服務
     * 存活條件。
     */
    private fun enforceProtectionState(): Boolean {
        val notifyEnabled = AccessibilityChecker.isNotificationsEnabled(this)
        val hasBoundApp = boundAppCount > 0
        if (notifyEnabled && hasBoundApp) return true
        Log.i(
            TAG,
            "Protection notification not warranted (notify=$notifyEnabled, " +
                "boundApps=$boundAppCount) — stopping service"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        return false
    }

    private fun refreshNotificationFromStatus() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(currentStatusText()))
    }

    /**
     * 此 service 只在「通知權限已授權 + 已綁定 App」時存活（由
     * [enforceProtectionState] 守門），正常文字一律是「防詐器保護中」。
     * 電池白名單未加入時背景可能被系統關閉，因此額外提示；無障礙服務為選用
     * 進階保護，不在此處作為警告條件。
     */
    private fun currentStatusText(): String {
        val batteryOk = AccessibilityChecker.isBatteryOptimizationIgnored(this)
        return if (batteryOk) {
            getString(R.string.foreground_service_text)
        } else {
            "⚠ 未加電池白名單 — 背景時可能被系統關閉"
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // deleteIntent: Android 14+ 允許滑掉 FGS 通知，這裡接住 dismiss 事件、
        // 立即 revive 服務、重 post 通知（< 50ms 視覺停留 ≈ 滑不掉）。
        val dismissIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(NotificationRevivalReceiver.ACTION_NOTIFICATION_DISMISSED)
                .setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.foreground_service_title))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissIntent)
            .build()
        // 顯式設定 FLAG_NO_CLEAR + FLAG_ONGOING_EVENT — Android ≤ 13 完全鎖死，
        // Android 14+ 雖然 OS 仍允許滑掉，但搭配 deleteIntent 即時 revive 達到視覺一致效果。
        notification.flags = notification.flags or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_ONGOING_EVENT
        return notification
    }

    companion object {
        private const val CHANNEL_ID = "anti_scam_foreground"
        private const val NOTIFICATION_ID = 4101
        private const val TAG = "AntiScamFgSvc"
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, AntiScamForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
