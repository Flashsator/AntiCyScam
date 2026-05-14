package com.anticyscam.app.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.anticyscam.app.domain.model.BoundApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the device's launchable apps so the user can pick which ones to
 * bind. Filters out our own package, plus disabled / system stub entries.
 *
 * NOTE: From Android 11+ this requires either the `QUERY_ALL_PACKAGES`
 * permission or a `<queries>` block in the manifest. Both are declared in
 * Phase 1's manifest.
 */
@Singleton
open class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    open suspend fun listLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        val resolves: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        val selfPkg = context.packageName

        resolves.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != selfPkg }
            .filter { it.enabled }
            .map { info ->
                InstalledAppInfo(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString()
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Best-effort lookup of a single app's label by package name. Returns
     * the packageName itself if the app has been uninstalled.
     */
    fun labelFor(packageName: String): String {
        val pm = context.packageManager
        return runCatching {
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        }.getOrDefault(packageName)
    }

    /**
     * Resolves a launch intent for the given package. Null if the app no
     * longer exposes a main launcher activity.
     */
    fun launchIntentFor(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    data class InstalledAppInfo(
        val packageName: String,
        val label: String
    ) {
        fun toBoundApp(): BoundApp = BoundApp(packageName = packageName, label = label)
    }
}
