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

@Singleton
class DailyAddTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AntiScamClock
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class State(
        val countToday: Int,
        val lockStartWall: Long,
        val lockStartElapsedNanos: Long
    ) {
        fun isLocked(now: NowSnapshot): Boolean = remainingLockMs(now) > 0L

        fun remainingLockMs(now: NowSnapshot): Long {
            if (lockStartWall == 0L) return 0L
            val wallDelta = (now.wallMillis - lockStartWall).coerceAtLeast(0L)
            val progressed = if (
                lockStartElapsedNanos == 0L ||
                now.elapsedNanos < lockStartElapsedNanos
            ) {
                // Reboot or pre-anchor row — fall back to wall delta, capped
                // so a fast-forwarded clock cannot dissolve the lock in one
                // jump. Matches TransferAccountSettleEngine.MAX_BOOT_WALL_DELTA_MS.
                wallDelta.coerceAtMost(MAX_BOOT_WALL_DELTA_MS)
            } else {
                val elapsedDeltaMs =
                    (now.elapsedNanos - lockStartElapsedNanos) / NANOS_PER_MILLI
                minOf(wallDelta, elapsedDeltaMs)
            }
            return (LOCK_DURATION_MS - progressed).coerceAtLeast(0L)
        }
    }

    fun snapshot(): State {
        val today = todayKey()
        val storedDay = prefs.getInt(KEY_DAY, -1)
        val count = if (storedDay == today) prefs.getInt(KEY_COUNT, 0) else 0
        val lockStartWall = prefs.getLong(KEY_LOCK_START_WALL, 0L)
        val lockStartElapsedNanos = prefs.getLong(KEY_LOCK_START_ELAPSED, 0L)
        return State(
            countToday = count,
            lockStartWall = lockStartWall,
            lockStartElapsedNanos = lockStartElapsedNanos
        )
    }

    fun recordAddAttempt(): Outcome {
        val now = clock.snapshot()
        val state = snapshot()
        if (state.isLocked(now)) {
            return Outcome.AlreadyLocked(state.remainingLockMs(now))
        }
        val nextCount = state.countToday + 1
        if (nextCount > DAILY_LOCK_THRESHOLD) {
            prefs.edit()
                .putInt(KEY_DAY, todayKey())
                .putInt(KEY_COUNT, nextCount)
                .putLong(KEY_LOCK_START_WALL, now.wallMillis)
                .putLong(KEY_LOCK_START_ELAPSED, now.elapsedNanos)
                .apply()
            return Outcome.HitLimit(LOCK_DURATION_MS)
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
        data class Allowed(val countToday: Int, val warning: Boolean) : Outcome
        data class HitLimit(val remainingMs: Long) : Outcome
        data class AlreadyLocked(val remainingMs: Long) : Outcome
    }

    companion object {
        const val DAILY_LOCK_THRESHOLD = 3
        const val WARNING_THRESHOLD = 3
        const val LOCK_DURATION_MS: Long = 24L * 60 * 60 * 1000
        const val MAX_BOOT_WALL_DELTA_MS: Long = 24L * 60 * 60 * 1000
        private const val NANOS_PER_MILLI: Long = 1_000_000L

        private const val PREFS_NAME = "daily_add_tracker"
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"
        private const val KEY_LOCK_START_WALL = "lock_start_wall"
        private const val KEY_LOCK_START_ELAPSED = "lock_start_elapsed_nanos"
    }
}
