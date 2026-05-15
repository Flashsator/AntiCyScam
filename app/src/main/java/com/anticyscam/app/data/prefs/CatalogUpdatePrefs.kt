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

private val Context.catalogUpdateDataStore by preferencesDataStore(name = "anticyscam_catalog_prefs")

/**
 * Tracks state for the in-app catalog-update prompt:
 *   - [lastCheckedAtMillis] — when we last fetched `version.json` from the
 *     public companion repo. Used to debounce checks to ~once per day.
 *   - [dismissedVersion] — the largest version the user has tapped "暫不更新"
 *     on. We don't re-prompt for that same version (or any earlier one).
 *   - [appliedVersion] — currently-applied downloaded catalog version. 0 means
 *     no override has been written and the bundled APK asset is in use.
 */
@Singleton
class CatalogUpdatePrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val lastCheckedAtMillis: Flow<Long> = context.catalogUpdateDataStore.data
        .map { it[KEY_LAST_CHECKED] ?: 0L }

    val dismissedVersion: Flow<Int> = context.catalogUpdateDataStore.data
        .map { it[KEY_DISMISSED_VERSION] ?: 0 }

    val appliedVersion: Flow<Int> = context.catalogUpdateDataStore.data
        .map { it[KEY_APPLIED_VERSION] ?: 0 }

    suspend fun lastCheckedAt(): Long = lastCheckedAtMillis.first()
    suspend fun dismissed(): Int = dismissedVersion.first()
    suspend fun applied(): Int = appliedVersion.first()

    suspend fun markCheckedAt(millis: Long) {
        context.catalogUpdateDataStore.edit { it[KEY_LAST_CHECKED] = millis }
    }

    suspend fun markDismissed(version: Int) {
        context.catalogUpdateDataStore.edit { it[KEY_DISMISSED_VERSION] = version }
    }

    suspend fun markApplied(version: Int) {
        context.catalogUpdateDataStore.edit { it[KEY_APPLIED_VERSION] = version }
    }

    /** Debug helper: wipe all keys so the next check behaves like a fresh install. */
    suspend fun clearAll() {
        context.catalogUpdateDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_LAST_CHECKED = longPreferencesKey("catalog_last_checked_at")
        val KEY_DISMISSED_VERSION = intPreferencesKey("catalog_dismissed_version")
        val KEY_APPLIED_VERSION = intPreferencesKey("catalog_applied_version")
    }
}
