package com.anticyscam.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted 30-second cooldown for the 2nd 臨時用 stage.
 *
 * The deadline is stored as a wall-clock timestamp so the user cannot pause
 * the countdown by pressing Home, force-stopping the activity, or even
 * killing the app — every resume continues from "remaining = deadline - now".
 *
 * Checkbox state is intentionally *not* persisted: per PRD, the three
 * "我確認…" boxes only become tappable AFTER the countdown ends, and they
 * gate one specific entry into the bank app. Re-entering the screen should
 * force re-confirmation.
 */
@Singleton
class ForcedCalmTimer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AntiScamClock
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Start (or restart) the 30-second window. Returns the deadline. */
    fun start(): Long {
        val deadline = clock.now() + DURATION_MS
        prefs.edit().putLong(KEY_DEADLINE, deadline).apply()
        return deadline
    }

    /**
     * Read the deadline if one is set. Returns 0 when no calm window is
     * active — callers must call [start] first.
     */
    fun deadline(): Long = prefs.getLong(KEY_DEADLINE, 0L)

    fun remainingMs(): Long {
        val deadline = deadline()
        if (deadline == 0L) return 0L
        return (deadline - clock.now()).coerceAtLeast(0L)
    }

    fun isFinished(): Boolean {
        val deadline = deadline()
        return deadline != 0L && clock.now() >= deadline
    }

    /** Clear once the user has successfully proceeded past stage 2. */
    fun clear() {
        prefs.edit().remove(KEY_DEADLINE).apply()
    }

    /**
     * Emits remaining-ms approximately every [tickMs] until the deadline
     * is reached, then emits 0 once and completes. Caller owns the
     * collection lifecycle (cancel on dispose).
     */
    fun observeRemaining(tickMs: Long = 100L): Flow<Long> = flow {
        while (true) {
            val remaining = remainingMs()
            emit(remaining)
            if (remaining <= 0L) return@flow
            delay(tickMs.coerceAtLeast(50L))
        }
    }

    companion object {
        const val DURATION_MS: Long = 30L * 1000

        private const val PREFS_NAME = "forced_calm_timer"
        private const val KEY_DEADLINE = "deadline"
    }
}
