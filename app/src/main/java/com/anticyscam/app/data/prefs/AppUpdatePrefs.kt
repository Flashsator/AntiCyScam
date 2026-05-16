package com.anticyscam.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appUpdateDataStore by preferencesDataStore(name = "anticyscam_app_update_prefs")

/**
 * Tracks state for the in-app APK-update prompt:
 *   - [lastCheckedAtMillis] — when we last fetched `app_version.json` from the
 *     public companion repo. Debounces checks to ~once per day.
 *   - [dismissedVersionCode] — the largest versionCode the user has tapped
 *     "暫不更新" on. We don't re-prompt for that same versionCode (or earlier).
 *
 * Mirrors [CatalogUpdatePrefs] but on a separate DataStore file so the
 * app-binary and catalog-data update flows never clobber each other's state.
 */
@Singleton
class AppUpdatePrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val lastCheckedAtMillis: Flow<Long> = context.appUpdateDataStore.data
        .map { it[KEY_LAST_CHECKED] ?: 0L }

    val dismissedVersionCode: Flow<Int> = context.appUpdateDataStore.data
        .map { it[KEY_DISMISSED_VERSION] ?: 0 }

    suspend fun lastCheckedAt(): Long = lastCheckedAtMillis.first()
    suspend fun dismissed(): Int = dismissedVersionCode.first()

    suspend fun markCheckedAt(millis: Long) {
        context.appUpdateDataStore.edit { it[KEY_LAST_CHECKED] = millis }
    }

    suspend fun markDismissed(versionCode: Int) {
        context.appUpdateDataStore.edit { it[KEY_DISMISSED_VERSION] = versionCode }
    }

    /** Debug helper: wipe all keys so the next check behaves like a fresh install. */
    suspend fun clearAll() {
        context.appUpdateDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_LAST_CHECKED = longPreferencesKey("app_update_last_checked_at")
        val KEY_DISMISSED_VERSION = intPreferencesKey("app_update_dismissed_version")
    }
}
