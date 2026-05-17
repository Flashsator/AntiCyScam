package com.anticyscam.app.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.anticyscam.app.R

/**
 * Device-admin scaffolding. When the user opts-in via system settings, the
 * app becomes a Device Administrator, which prevents accidental uninstall.
 *
 * 需求 #3：開啟「裝置管理員」是進入防詐器主功能的三項條件之一。Receiver 本身
 * 不主動觸發任何政策動作 — 啟用態僅用於把「使用者可一鍵移除 App」的風險墊高，
 * 並讓 [com.anticyscam.app.utils.SystemAccessChecker.isDeviceAdminActive]
 * 取得正確的狀態。
 *
 * [onDisableRequested] 在使用者於系統設定按下「停用裝置管理員」時觸發；回傳的
 * 字串會被系統當成確認對話框內容顯示，讓使用者知道關閉後將失去防誤刪保護，
 * 對應需求 #2「永遠不能關閉」的提醒語意。
 */
class AntiScamDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_disable_warning)
    }
}
