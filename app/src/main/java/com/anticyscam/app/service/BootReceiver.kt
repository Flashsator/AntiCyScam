package com.anticyscam.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the foreground service after a device boot or app upgrade so the
 * AccessibilityService stays alive even if the user has not yet opened the
 * app. Registered for BOOT_COMPLETED + MY_PACKAGE_REPLACED in the manifest.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            AntiScamForegroundService.start(context.applicationContext)
        }
    }
}
