package com.anticyscam.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.anticyscam.app.service.AntiScamAccessibilityService

/**
 * Inspects system settings to determine whether [AntiScamAccessibilityService]
 * is currently enabled. This is the single source of truth used by the
 * accessibility gate.
 *
 * Why not use [android.view.accessibility.AccessibilityManager.isEnabled]?
 *   That returns true if ANY accessibility service is enabled — not
 *   specifically ours. We need the per-service answer, so we parse the
 *   Settings.Secure value directly.
 */
object AccessibilityChecker {

    /**
     * Returns true only when our service is in the system's enabled list AND
     * the global accessibility switch is on. Both must hold; older OEMs have
     * been observed to keep the service entry but disable the master switch.
     */
    fun isOurServiceEnabled(context: Context): Boolean {
        val expectedId = ComponentName(
            context,
            AntiScamAccessibilityService::class.java
        ).flattenToString()

        val masterSwitchOn = runCatching {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        }.getOrDefault(0) == 1

        if (!masterSwitchOn) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        if (enabledServices.isEmpty()) return false

        val splitter = TextUtils.SimpleStringSplitter(SERVICE_DELIMITER)
        splitter.setString(enabledServices)
        for (entry in splitter) {
            // ComponentName.flattenToString() is canonical; some manufacturers
            // write the short form, so we also compare case-insensitively
            // against the short flattenable form as a fallback.
            if (entry.equals(expectedId, ignoreCase = true)) return true
        }
        return false
    }

    /**
     * Intent that opens the system Accessibility settings page. The user has
     * to manually find our service and toggle it on; Android does not allow
     * apps to programmatically enable themselves (by design).
     */
    fun openSettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private const val SERVICE_DELIMITER = ':'
}
