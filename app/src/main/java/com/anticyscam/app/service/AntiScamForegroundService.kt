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
import com.anticyscam.app.utils.SystemAccessChecker
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
 * OEMs (Xiaomi, Oppo, Huawei) so the [UsageStatsForegroundDetector] foreground
 * watcher does not get silently killed by the background-app reaper. A
 * persistent notification is required by Android — we use it to surface the
 * protection status as well.
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
    // 前景偵測（UsageStats 輪詢）—— 唯一的偵測引擎。由 [updateDetectionMode]
    // 依「使用情況存取權 + 上層顯示」是否齊備啟動／停止。
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
        // 確保前景判決核心的綁定 App 快照被填入後，UsageStats 偵測才有資料
        // 可比對。start() 為冪等。
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
     * 依特殊權限狀態切換前景偵測。[UsageStatsForegroundDetector] 是唯一的偵測
     * 引擎，但前提是 PACKAGE_USAGE_STATS 與上層顯示權限都已授權 —— 缺任一項，
     * 偵測到 App 也無法跳出警告，因此直接停掉。
     * 冪等：detector 的 start／stop 重複呼叫皆為 no-op，可安全於 watchdog 反覆呼叫。
     */
    private fun updateDetectionMode() {
        val canDetect = SystemAccessChecker.hasUsageStatsPermission(this) &&
            SystemAccessChecker.canDrawOverlays(this)
        if (canDetect) {
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
     * 週期性重新評估守門狀態與偵測權限。使用者可能在執行期間給／收回
     * 「使用情況存取權」「上層顯示」，每個 tick 重新評估並切換 UsageStats
     * 偵測的啟停。Phase 3 Android 12 可靠性修補。
     */
    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                runCatching {
                    if (!enforceProtectionState()) return@launch
                    // 偵測權限可能在執行期間被使用者開啟／關閉 —— 每個 tick
                    // 重新評估，必要時切換 UsageStats 偵測的啟停。
                    updateDetectionMode()
                    refreshNotificationFromStatus()
                    // 處理使用者後來給/收回 READ_PHONE_STATE / READ_MEDIA_AUDIO 的情況
                    callRecordingDetector.refreshIfNeeded()
                }.onFailure { Log.w(TAG, "watchdog tick failed", it) }
            }
        }
    }

    /**
     * 守門檢查 — 需求 #4：服務只在「已綁定至少一個 App」時存活。未綁定 App
     * 時沒有保護目標，因此 stopSelf，常駐通知「防詐器保護中」一併消失。
     *
     * 通知權限為選用：未授權時服務照常運作、照常偵測與跳警告，只是系統不
     * 顯示常駐通知。裝置管理員／電池白名單同為選用的進階保護，皆不作為服務
     * 存活條件。
     */
    private fun enforceProtectionState(): Boolean {
        if (boundAppCount > 0) return true
        Log.i(TAG, "No bound app (boundApps=$boundAppCount) — stopping service")
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
     * 此 service 只在「已綁定 App」時存活（由 [enforceProtectionState] 守門），
     * 通知文字一律是「防詐器保護中」。電池白名單為選用項目，未加入時不再於
     * 通知顯示警告，避免常駐通知一直帶警告字樣造成使用者焦慮 —— 入口改放在
     * 設定頁的「電池白名單（選用）」。
     */
    private fun currentStatusText(): String =
        getString(R.string.foreground_service_text)

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
