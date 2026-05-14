package com.anticyscam.app.testing

import android.content.ContextWrapper
import com.anticyscam.app.data.system.InstalledAppsProvider
import com.anticyscam.app.data.system.InstalledAppsProvider.InstalledAppInfo

/**
 * Test double for [InstalledAppsProvider] that returns a fixed list of
 * launchable apps. The Context dependency is satisfied by a null-backed
 * [ContextWrapper] because tests never invoke [labelFor] or
 * [launchIntentFor] (which would actually need a PackageManager).
 */
class FakeInstalledAppsProvider(
    private val apps: List<InstalledAppInfo>
) : InstalledAppsProvider(ContextWrapper(null)) {

    override suspend fun listLaunchableApps(): List<InstalledAppInfo> = apps
}
