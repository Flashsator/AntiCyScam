package com.anticyscam.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks how many transfer accounts the user has added on the current
 * calendar day. Triggers a 24h add-lock once the count reaches
 * [DAILY_LOCK_THRESHOLD], per PRD § 3.2.
 *
 * Key design notes:
 *  - Date bucket is keyed by `yyyymmdd` (local calendar) — automatic rollover
 *    at midnight means a forced re-bucket isn't needed.
 *  - Lock deadline is a wall-clock timestamp; we deliberately do NOT use
 *    elapsedRealtime so the lock survives reboots (reboot shouldn't be a
 *    bypass vector here).
 *  - The "modify the account number" path counts as a fresh add for both
 *    the daily count and the per-account cooldown (PRD § 3.2.修改帳號).
 */
@Singleton
class DailyAddTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AntiScamClock
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Snapshot used by status checks and UI. */
    data class State(
        val countToday: Int,
        val lockUntil: Long
    ) {
        fun isLocked(now: Long): Boolean = now < lockUntil
        fun remainingLockMs(now: Long): Long = (lockUntil - now).coerceAtLeast(0L)
    }

    fun snapshot(): State {
        val today = todayKey()
        val storedDay = prefs.getInt(KEY_DAY, -1)
        val count = if (storedDay == today) prefs.getInt(KEY_COUNT, 0) else 0
        val lockUntil = prefs.getLong(KEY_LOCK_UNTIL, 0L)
        return State(countToday = count, lockUntil = lockUntil)
    }

    /**
     * Atomically increment today's add count and decide what to do. Result
     * categories drive both the persisted lock and the UI route.
     */
    fun recordAddAttempt(): Outcome {
        val now = clock.now()
        val state = snapshot()
        if (state.isLocked(now)) {
            return Outcome.AlreadyLocked(state.remainingLockMs(now))
        }
        val nextCount = state.countToday + 1
        if (nextCount >= DAILY_LOCK_THRESHOLD) {
            val lockUntil = now + LOCK_DURATION_MS
            prefs.edit()
                .putInt(KEY_DAY, todayKey())
                .putInt(KEY_COUNT, nextCount)
                .putLong(KEY_LOCK_UNTIL, lockUntil)
                .apply()
            return Outcome.HitLimit(lockUntil)
        }
        prefs.edit()
            .putInt(KEY_DAY, todayKey())
            .putInt(KEY_COUNT, nextCount)
            .apply()
        return Outcome.Allowed(
            countToday = nextCount,
            warning = nextCount >= WARNING_THRESHOLD
        )
    }

    fun observeState(): Flow<State> = callbackFlow {
        trySend(snapshot())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(snapshot())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Test / debug only — wipe the bucket. */
    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun todayKey(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 10000 +
            (cal.get(Calendar.MONTH) + 1) * 100 +
            cal.get(Calendar.DAY_OF_MONTH)
    }

    sealed interface Outcome {
        /** Add can proceed. If [warning] is true the UI should show "再新增 1 筆將觸發警告". */
        data class Allowed(val countToday: Int, val warning: Boolean) : Outcome
        /** This very call triggered the limit. UI should pivot to the fullscreen lockdown. */
        data class HitLimit(val lockUntil: Long) : Outcome
        /** Add was attempted while already locked. */
        data class AlreadyLocked(val remainingMs: Long) : Outcome
    }

    companion object {
        const val DAILY_LOCK_THRESHOLD = 3
        const val WARNING_THRESHOLD = 2
        const val LOCK_DURATION_MS: Long = 24L * 60 * 60 * 1000

        private const val PREFS_NAME = "daily_add_tracker"
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"
        private const val KEY_LOCK_UNTIL = "lock_until"
    }
}
