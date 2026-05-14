package com.anticyscam.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sliding-window counter for "臨時用" invocations.
 *
 * PRD § 3.3:
 *  - 10-min window
 *  - 1st →  pass-through (warning text only)
 *  - 2nd →  forced 30s calm + checklist
 *  - 3rd →  red lockdown + auto-dial 165
 *  - After 3rd → 5min lockout, then counter resets
 *
 * Persistence model: we store
 *   - `first_use_at`  — wall-clock of the most-recent "first" hit
 *   - `count_in_window` — number of hits inside that 10-min bucket
 *   - `lockout_until` — wall-clock deadline of the post-3rd 5-min lockout
 *
 * Wall-clock is intentional: changing system date doesn't bypass the gate
 * because going BACKWARDS in time still satisfies `now < lockoutUntil` and
 * going forwards only burns the 10-min window earlier, which is no help to
 * a fraudster.
 *
 * Plan v4 Item 3 (scope clarification): this counter is GLOBAL across all
 * bound apps. Selecting "臨時用" while opening 網銀 A and then later opening
 * 網銀 B both feed the same 10-min bucket — switching launcher apps does
 * NOT reset the counter. The single SharedPreferences file used here has
 * no per-package partitioning by design.
 */
@Singleton
class TempUseTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AntiScamClock
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class Stage { FIRST, SECOND, THIRD, LOCKED_OUT }

    data class Snapshot(
        val stage: Stage,
        val countInWindow: Int,
        val windowEndsAt: Long,
        val lockoutUntil: Long
    )

    fun snapshot(): Snapshot {
        val now = clock.now()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now < lockoutUntil) {
            return Snapshot(Stage.LOCKED_OUT, 0, 0L, lockoutUntil)
        }
        val firstUseAt = prefs.getLong(KEY_FIRST_USE_AT, 0L)
        val rawCount = prefs.getInt(KEY_COUNT, 0)
        val withinWindow = firstUseAt != 0L && now - firstUseAt < WINDOW_MS
        val count = if (withinWindow) rawCount else 0
        val stage = when (count) {
            0 -> Stage.FIRST
            1 -> Stage.SECOND
            else -> Stage.THIRD
        }
        return Snapshot(
            stage = stage,
            countInWindow = count,
            windowEndsAt = if (withinWindow) firstUseAt + WINDOW_MS else 0L,
            lockoutUntil = lockoutUntil
        )
    }

    /**
     * Commit one "臨時用" tap. Returns the *resulting* stage that the UI
     * should show. After THIRD is consumed the 5-min lockout is set.
     */
    fun consume(): Stage {
        val now = clock.now()
        val current = snapshot()
        if (current.stage == Stage.LOCKED_OUT) return Stage.LOCKED_OUT

        val isFirstInWindow = current.countInWindow == 0
        val newCount = current.countInWindow + 1
        val firstAt = if (isFirstInWindow) now else prefs.getLong(KEY_FIRST_USE_AT, now)

        val editor = prefs.edit()
            .putLong(KEY_FIRST_USE_AT, firstAt)
            .putInt(KEY_COUNT, newCount)

        val stage = when (newCount) {
            1 -> Stage.FIRST
            2 -> Stage.SECOND
            else -> Stage.THIRD
        }
        if (stage == Stage.THIRD) {
            editor.putLong(KEY_LOCKOUT_UNTIL, now + LOCKOUT_MS)
        }
        editor.apply()
        return stage
    }

    /**
     * Called by the stage-3 activity once the 5-minute cooldown has elapsed
     * (or by a test) to reset the window for the next batch of attempts.
     */
    fun clearLockoutIfElapsed() {
        val now = clock.now()
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now >= until && until != 0L) {
            prefs.edit()
                .remove(KEY_LOCKOUT_UNTIL)
                .remove(KEY_FIRST_USE_AT)
                .remove(KEY_COUNT)
                .apply()
        }
    }

    /** Test / debug only. */
    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val WINDOW_MS: Long = 10L * 60 * 1000
        const val LOCKOUT_MS: Long = 5L * 60 * 1000

        private const val PREFS_NAME = "temp_use_tracker"
        private const val KEY_FIRST_USE_AT = "first_use_at"
        private const val KEY_COUNT = "count"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
