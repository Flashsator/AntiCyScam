package com.anticyscam.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Time and app-open-count source of truth used by cooldown logic.
 *
 * Multiple cooldown gates rely on these primitives, so we centralize them
 * to make a future swap to a tamper-resistant clock (e.g. server-issued
 * timestamps) a single-line change.
 *
 * Backed by [SharedPreferences] rather than DataStore because we need a
 * synchronous read inside pure status calculations. The data is non-sensitive
 * (just a monotonic counter).
 */
@Singleton
class AntiScamClock @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun now(): Long = System.currentTimeMillis()

    fun appOpenCount(): Int = prefs.getInt(KEY_OPEN_COUNT, 0)

    /**
     * Increment + return the new count. Called from MainActivity.onCreate so
     * each visible cold-start advances the counter. Process-restart-only
     * counters are intentional — we don't want navigation churn to inflate
     * the gate.
     */
    fun incrementOpenCount(): Int {
        // Monotonic and saturating — never decreases, never wraps.
        val next = (appOpenCount() + 1).coerceAtLeast(1)
        prefs.edit().putInt(KEY_OPEN_COUNT, next).apply()
        return next
    }

    fun observeOpenCount(): Flow<Int> = callbackFlow {
        trySend(appOpenCount())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_OPEN_COUNT) trySend(appOpenCount())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private companion object {
        const val PREFS_NAME = "anti_scam_clock"
        const val KEY_OPEN_COUNT = "app_open_count"
    }
}
