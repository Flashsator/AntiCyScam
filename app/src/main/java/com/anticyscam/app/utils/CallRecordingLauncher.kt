package com.anticyscam.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log

/**
 * 引導使用者到「系統通話錄音」設定的地方。
 *
 * Android 9+ 起禁止第三方 App 擷取通話麥克風 stream，所以本 App 只能引導使用者
 * 開啟手機內建撥號程式 (Dialer) 自帶的通話自動錄音功能。各 OEM 的設定 activity
 * 路徑差異大且每個大版本可能變，這裡只用「公開且穩定」的 action：
 *
 *   1. [TelecomManager.ACTION_SHOW_CALL_SETTINGS]（API 19+）—— 由系統路由到
 *      當前預設電話 App 的「通話設定」根頁；「通話錄音」通常就在那一頁，最壞
 *      情況也只需要再點一層。
 *   2. [Intent.ACTION_DIAL]（無號碼）—— 至少把使用者帶到電話 App，自己進 ⋮ →
 *      設定。給 OEM 把 CALL_SETTINGS 關閉的怪 ROM 保底。
 *   3. [Settings.ACTION_SETTINGS]—— 系統設定首頁，終極保底。
 *
 * 對於原生 Pixel 等沒有內建通話錄音的機型，這幾條 Intent 都會成功啟動，但設定頁
 * 裡找不到「通話錄音」選項。UI 端用同一張 Howto Card 文字導引使用者改用其他方法。
 */
object CallRecordingLauncher {

    fun launch(context: Context): LaunchResult {
        val candidates = listOf(
            Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        // 只有第 0 個（通話設定根頁）才算「直接到位」，其他 fallback 都需要使用者
        // 自己再點到設定 → 通話錄音，UI 端要 toast 提示。
        return tryLaunchChain(context, candidates, directIndices = setOf(0))
    }

    private fun tryLaunchChain(
        context: Context,
        candidates: List<Intent>,
        directIndices: Set<Int>
    ): LaunchResult {
        var lastError: Throwable? = null
        for ((idx, intent) in candidates.withIndex()) {
            try {
                context.startActivity(intent)
                return LaunchResult(
                    launched = true,
                    isDirect = idx in directIndices,
                    attemptIndex = idx,
                    error = null
                )
            } catch (t: Throwable) {
                Log.w(TAG, "startActivity #$idx (${intent.action}) failed: ${t.javaClass.simpleName}", t)
                lastError = t
            }
        }
        return LaunchResult(
            launched = false,
            isDirect = false,
            attemptIndex = -1,
            error = lastError
        )
    }

    private const val TAG = "CallRecordingLauncher"
}
