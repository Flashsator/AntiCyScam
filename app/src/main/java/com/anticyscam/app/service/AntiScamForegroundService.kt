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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchdogJob: Job? = null
    // 通話結束自動偵測 → 掃 OEM 錄音檔 → 通知使用者送進辨識流程
    private val callRecordingDetector by lazy { CallRecordingDetector(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // 需求 #4：通知欄「防詐器保護中」只允許在三項條件全綠時出現。
        // 啟動瞬間先以低重要性 placeholder 滿足 startForeground 義務，再把守門
        // 邏輯交給 [enforceProtectionState] 決定要不要 stopSelf。
        startForegroundCompat()
        if (!enforceProtectionState()) return
        startWatchdog()
        callRecordingDetector.start()
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
        startForegroundCompat()
        if (!enforceProtectionState()) return START_NOT_STICKY
        startWatchdog()
        return START_STICKY
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        callRecordingDetector.destroy()
        scope.cancel()
        super.onDestroy()
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
                    refreshNotificationFromStatus()
                    // 處理使用者後來給/收回 READ_PHONE_STATE / READ_MEDIA_AUDIO 的情況
                    callRecordingDetector.refreshIfNeeded()
                }.onFailure { Log.w(TAG, "watchdog tick failed", it) }
            }
        }
    }

    /**
     * 三項守門檢查 — 任一不符就 stopSelf。v1 邏輯刻意保持簡單：使用者必須先把
     * 四項都打開才有保護，少一項就把服務拉下、讓 App 進入 gate 狀態。
     */
    private fun enforceProtectionState(): Boolean {
        val a11yEnabled = AccessibilityChecker.isOurServiceEnabled(this)
        val adminActive = AccessibilityChecker.isDeviceAdminActive(this)
        val notifyEnabled = AccessibilityChecker.isNotificationsEnabled(this)
        if (a11yEnabled && adminActive && notifyEnabled) return true
        Log.i(
            TAG,
            "Protection requirements not all met (a11y=$a11yEnabled, " +
                "admin=$adminActive, notify=$notifyEnabled) — stopping service"
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
     * 此 service 只在三項條件全綠時存活（由 [enforceProtectionState] 守門），
     * 因此正常情況下文字一律是「防詐器保護中」。仍保留 a11y 連線狀態與電池
     * 白名單兩項可觀察的健康欄位作為更細的回饋（會在 watchdog tick 之間自然
     * 漂移；若使用者在系統設定關閉了任一三項條件，下一次 tick 直接 stopSelf）。
     */
    private fun currentStatusText(): String {
        val alive = AntiScamAccessibilityService.isAlive.value
        val batteryOk = AccessibilityChecker.isBatteryOptimizationIgnored(this)
        return when {
            !alive -> "⚠ 服務未連線 — 請重新啟動 App"
            !batteryOk -> "⚠ 未加電池白名單 — 背景時可能被系統關閉"
            else -> getString(R.string.foreground_service_text)
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
