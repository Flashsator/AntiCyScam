package com.anticyscam.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.anticyscam.app.R
import com.anticyscam.app.ui.recognition.RecognitionActivity

/**
 * 通話結束後，若 [com.anticyscam.app.data.system.CallRecordingScanner] 找到
 * OEM 自動錄音檔，就丟一則 HIGH 重要性通知，使用者點下後直接送進 RecognitionActivity
 * 既有的 ACTION_SEND 音檔分享流程進行語音辨識。
 *
 * 重點：通知的 PendingIntent 直接帶 EXTRA_STREAM，不另開新的 entry。
 * RecognitionActivity 已經有對應 intent-filter，可以零修改接住。
 */
object CallRecordingNotifier {

    private const val CHANNEL_ID = "call_recording_ready"
    private const val NOTIFICATION_ID = 4201

    fun postRecordingFound(context: Context, recordingUri: Uri) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // Explicit component target — 直接指名 RecognitionActivity，避開 ACTION_SEND
        // 被當成系統分享單由使用者選 App 的 chooser 情況。
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            component = ComponentName(context, RecognitionActivity::class.java)
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, recordingUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            sendIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.notif_call_recording_title))
            .setContentText(context.getString(R.string.notif_call_recording_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_call_recording),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_call_recording_text)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }
}
