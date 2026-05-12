package com.anticyscam.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "anticyscam_prefs")

/**
 * Lightweight key-value preferences (NOT for sensitive data — encrypted store
 * for transfer accounts is introduced in Phase 5).
 *
 * Currently tracks:
 *   - hasCompletedFirstLaunch: whether the user has gone through the
 *     accessibility gate at least once. Used to decide whether to show
 *     extended onboarding copy.
 *   - hasSeenOverlayPermissionPrompt: whether we've shown the overlay
 *     permission flow (used later in Phase 8).
 */
@Singleton
class AppPreferences @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val context: Context
) {
    val hasCompletedFirstLaunch: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_FIRST_LAUNCH_DONE] ?: false }

    suspend fun markFirstLaunchComplete() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH_DONE] = true
        }
    }

    val hasSeenOverlayPrompt: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_OVERLAY_PROMPT_SEEN] ?: false }

    suspend fun markOverlayPromptSeen() {
        context.dataStore.edit { prefs ->
            prefs[KEY_OVERLAY_PROMPT_SEEN] = true
        }
    }

    private companion object {
        val KEY_FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
        val KEY_OVERLAY_PROMPT_SEEN = booleanPreferencesKey("overlay_prompt_seen")
    }
}
