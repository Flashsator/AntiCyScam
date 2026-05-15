package com.anticyscam.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.anticyscam.app.data.system.CallRecordingScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通話狀態偵測：在 FGS 內常駐，註冊 [TelephonyCallback.CallStateListener]
 * 追蹤通話從 OFFHOOK → IDLE 的瞬間，等 2.5 秒讓 OEM 錄音 App 把檔案寫完 +
 * MediaStore 完成索引，呼叫 [CallRecordingScanner] 找剛寫入的錄音檔，
 * 找到就讓 [CallRecordingNotifier] 推一則高優先通知。
 *
 * 設計：
 *  - 只在最低權限（READ_PHONE_STATE）拿到後才實際註冊 listener，沒授權就 no-op
 *  - 提供 [refreshIfNeeded] 讓 FGS watchdog tick 處理「使用者後來授權 / 取消」狀況
 *  - 不持有 Context 以外狀態，stop() 後可重新 start
 *  - minSdk 31，直接用 TelephonyCallback（API 31+）不再相容舊 PhoneStateListener
 */
class CallRecordingDetector(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val telephony: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private var callback: TelephonyCallback? = null
    private var lastState: Int = TelephonyManager.CALL_STATE_IDLE
    private var callStartedAt: Long = 0L
    private var scanJob: Job? = null

    fun start() {
        if (!hasRequiredPermission()) {
            Log.d(TAG, "start skipped — READ_PHONE_STATE not granted")
            return
        }
        val tm = telephony ?: run {
            Log.w(TAG, "start skipped — TelephonyManager null")
            return
        }
        if (callback != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // minSdk 31，理論上不會走到這裡；保險起見不做 PhoneStateListener fallback
            Log.w(TAG, "start skipped — API < 31 not supported")
            return
        }
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleStateTransition(state)
            }
        }
        runCatching {
            tm.registerTelephonyCallback(context.mainExecutor, cb)
            callback = cb
            Log.i(TAG, "TelephonyCallback registered")
        }.onFailure {
            Log.w(TAG, "registerTelephonyCallback failed", it)
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        val cb = callback ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { telephony?.unregisterTelephonyCallback(cb) }
                .onFailure { Log.w(TAG, "unregister failed", it) }
        }
        callback = null
    }

    /**
     * 在 FGS watchdog tick 呼叫。處理兩種情況：
     *  - 之前沒授權 → 現在授權了：start() 把 listener 接上
     *  - 之前授權 → 現在被撤回：stop() 把 listener 拆掉，避免 SecurityException
     */
    fun refreshIfNeeded() {
        val granted = hasRequiredPermission()
        val registered = callback != null
        when {
            granted && !registered -> start()
            !granted && registered -> stop()
        }
    }

    private fun handleStateTransition(newState: Int) {
        val previous = lastState
        lastState = newState
        when {
            previous == TelephonyManager.CALL_STATE_IDLE &&
                newState == TelephonyManager.CALL_STATE_OFFHOOK -> {
                callStartedAt = System.currentTimeMillis()
                Log.d(TAG, "call started at $callStartedAt")
            }
            previous == TelephonyManager.CALL_STATE_OFFHOOK &&
                newState == TelephonyManager.CALL_STATE_IDLE -> {
                val startedAt = callStartedAt
                if (startedAt <= 0L) return
                Log.d(TAG, "call ended, scheduling scan in ${SCAN_DELAY_MS}ms")
                scanJob?.cancel()
                scanJob = scope.launch {
                    delay(SCAN_DELAY_MS)
                    val uri = CallRecordingScanner.findLatestRecording(context, startedAt)
                    if (uri != null) {
                        CallRecordingNotifier.postRecordingFound(context, uri)
                    }
                }
            }
        }
    }

    private fun hasRequiredPermission(): Boolean {
        val phoneOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val mediaPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val mediaOk = ContextCompat.checkSelfPermission(context, mediaPerm) ==
            PackageManager.PERMISSION_GRANTED
        return phoneOk && mediaOk
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    companion object {
        private const val TAG = "CallRecordingDetector"
        // 給 OEM 錄音 App 收尾 + MediaStore 索引完成的緩衝
        private const val SCAN_DELAY_MS = 2_500L
    }
}
