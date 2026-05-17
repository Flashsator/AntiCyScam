package com.anticyscam.app.service

import android.content.Context
import android.content.Intent
import com.anticyscam.app.data.system.InstalledAppsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launches a bound app **with authorization** so [UsageStatsForegroundDetector]
 * does NOT trigger the blocking warning.
 *
 * Order matters: authorize THEN start the activity. The foreground poll may
 * observe the bound app surfacing before the launching activity's
 * `startActivity` returns, so the grant must already be in place when the
 * detector consults the tracker.
 */
@Singleton
class AppLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installedApps: InstalledAppsProvider,
    private val tracker: AuthorizedLaunchTracker
) {

    /**
     * Returns true if the app was found and started; false if the target
     * package is no longer installed or has no launcher activity.
     */
    fun launchAuthorized(packageName: String): Boolean {
        val intent = installedApps.launchIntentFor(packageName) ?: return false
        tracker.authorize(packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
