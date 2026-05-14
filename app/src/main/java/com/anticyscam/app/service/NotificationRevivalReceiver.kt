package com.anticyscam.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 通知被使用者滑掉時觸發。
 *
 * Android 14+ 強制允許使用者滑掉前景服務通知（OS 控制權設計，[Notification.FLAG_NO_CLEAR]、
 * `setOngoing(true)` 等都不再保證有效）。我們走唯一可行路徑：滑掉的瞬間立刻把服務
 * 重新拉起、重新 post 通知 — 視覺上幾乎等於「滑不掉」（< 50ms 視覺停留）。
 *
 * 此 receiver 為 deleteIntent 的目標；[AntiScamForegroundService.buildNotification] 設定。
 */
class NotificationRevivalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "FGS notification dismissed by user — revival triggered")
        runCatching { AntiScamForegroundService.start(context) }
            .onFailure { Log.w(TAG, "revival start failed", it) }
    }

    companion object {
        const val ACTION_NOTIFICATION_DISMISSED = "com.anticyscam.app.NOTIFICATION_DISMISSED"
        private const val TAG = "NotifRevival"
    }
}
