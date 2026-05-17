package com.anticyscam.app.utils

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.anticyscam.app.R
import com.anticyscam.app.service.AntiScamDeviceAdminReceiver

/**
 * Inspects system settings to determine the state of the special permissions
 * the app depends on: battery-optimization whitelist, overlay (上層顯示),
 * usage-access (PACKAGE_USAGE_STATS), device admin, and notifications.
 *
 * None of these can be granted at runtime — they live behind dedicated system
 * settings pages — so this object both *reads* their state and produces the
 * [Intent]s that route the user to the right page to flip them on.
 */
object SystemAccessChecker {

    /**
     * 是否已加入電池白名單。Android 12+ doze 模式在背景時會更激進，未加白名單
     * 常見症狀是前景服務在背景被系統關閉。屬選用的進階保護項目。
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 是允許 App 直接彈系統對話框的
     * 敏感 Intent，需在 manifest 宣告對應權限（已加）。Play 商店允許此用法，
     * 條件是 App 屬於需要持續背景運作的類別（防詐 / 即時通訊 / 健康追蹤）。
     */
    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemptionIntent(packageName: String): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * 是否已授予 SYSTEM_ALERT_WINDOW（上層顯示）。前景偵測偵測到未授權開啟
     * 已綁定 App 時，靠此權限才能蓋出全螢幕警告。
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openOverlayPermissionIntent(packageName: String): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * 是否已授予 PACKAGE_USAGE_STATS（使用情況存取）特殊權限。前景偵測
     * [com.anticyscam.app.service.UsageStatsForegroundDetector] 靠
     * `UsageStatsManager` 輪詢前景 App，沒有此權限 `queryEvents` 一律回空。
     *
     * 此權限無法用 runtime request 取得，只能由使用者在「使用情況存取權」
     * 設定頁手動開啟，因此用 [AppOpsManager] 查授權狀態 —— 一般 permission
     * 檢查 API 對特殊存取權無效。
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = runCatching {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 開啟「使用情況存取權」系統設定頁。帶上 package URI 讓多數 ROM 直接
     * 捲到本 App；少數舊 ROM 不吃 data，會落到清單頁，使用者需手動點「防詐器」。
     */
    fun openUsageAccessSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * 是否已啟用 [AntiScamDeviceAdminReceiver] 為裝置管理員。透過
     * [DevicePolicyManager.isAdminActive] 直接詢問系統。
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        val admin = ComponentName(context, AntiScamDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    /**
     * 啟動「啟用裝置管理員」流程。
     *
     * 為什麼把 `ACTION_ADD_DEVICE_ADMIN` 從第一順位移除？
     *   Android 12 低配 OEM ROM 上，`startActivity(ACTION_ADD_DEVICE_ADMIN)` 表面成功
     *   （`resolveActivity` 回非 null、Activity 也有啟動），但系統會在進入後立刻
     *   silent finish — 使用者看到「螢幕閃一下又退回 App」，以為 App 壞了。
     *   先嘗試清單頁（`DEVICE_ADMIN_SETTINGS`）讓使用者主動點選反而最穩定。
     *
     * Fallback 順序（每個都實際 try `startActivity`、失敗才往下走）：
     *   1. `DEVICE_ADMIN_SETTINGS`（裝置管理員清單）— 大多數 OEM 穩定
     *   2. `ACTION_ADD_DEVICE_ADMIN`（標準同意對話框）— 高階機可一鍵
     *   3. `ACTION_SECURITY_SETTINGS`（安全性首頁）— 上述都不行時的保底入口
     *   4. `ACTION_SETTINGS`（系統設定）— 終極保底
     */
    fun launchDeviceAdminEnable(context: Context): LaunchResult {
        val admin = ComponentName(context, AntiScamDeviceAdminReceiver::class.java)
        val candidates = listOf(
            Intent(ACTION_DEVICE_ADMIN_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.device_admin_description)
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        // 任何一個成功都不算「直接彈確認框」— 使用者一律要手動點清單上的「防詐器」。
        return tryLaunchChain(context, candidates, directIndices = emptySet())
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

    /**
     * 通知權限總開關。Android 13+ 透過 POST_NOTIFICATIONS runtime 權限；
     * 舊版本則由系統設定「應用通知」決定。[NotificationManagerCompat] 在兩種
     * 情境下都回傳一致的布林值。
     */
    fun isNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * 直接開啟本 App 的「應用程式通知」設定頁；可用於 Android 13+ 使用者拒絕
     * POST_NOTIFICATIONS runtime 後重新引導，也可用於舊版 OEM 在通知總開關被
     * 關閉時提供入口。
     */
    fun openAppNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private const val TAG = "SystemAccessChecker"

    // 隱藏 action — AOSP/OEM 支援度不一，作為主要入口比 ADD_DEVICE_ADMIN 穩定。
    private const val ACTION_DEVICE_ADMIN_SETTINGS =
        "android.settings.DEVICE_ADMIN_SETTINGS"
}

/**
 * `startActivity` 鏈式嘗試的結果。
 *
 *   - [launched]：是否任一個 candidate 成功啟動（沒拋 exception）
 *   - [isDirect]：成功的 candidate 是否能直接帶到目標位置。false 表示落到
 *     清單頁或主設定頁，UI 端應 toast 提示使用者手動找到「防詐器」並開啟。
 *   - [attemptIndex]：成功的 candidate 在原本鏈中的索引；全失敗時為 -1
 *   - [error]：全失敗時的最後一個例外（用於 debug toast）
 */
data class LaunchResult(
    val launched: Boolean,
    val isDirect: Boolean,
    val attemptIndex: Int,
    val error: Throwable?
)
