package com.anticyscam.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sliding-window counter for "臨時用" invocations.
 *
 * Phase 1 — base behavior (PRD § 3.3):
 *  - 10-min window
 *  - 1st →  pass-through (warning text only)
 *  - 2nd →  forced 30s calm + checklist
 *  - 3rd →  red lockdown + auto-dial 165
 *  - After 3rd → 5-min lockout
 *
 * Phase 2 — escalation tier (this revision):
 *  - When the 5-min lockout elapses, instead of a silent reset we enter a
 *    10-minute WATCHFUL period. The main screen surfaces an orange warning
 *    bar with countdown during this window.
 *  - If the user hits the 3rd warning again WITHIN that 10-minute watchful
 *    window → escalate to a 1-hour BAN that blocks every transfer-related
 *    feature in the app (臨時用 launch, picker, add/edit transfer account,
 *    dial/copy). The scam-info zone and unrelated settings stay reachable.
 *  - If the watchful window elapses without another 3rd hit → silent reset.
 *  - If the 1-hour ban elapses → full reset to FIRST.
 *
 * Persistence model (all wall-clock, intentionally — see below):
 *   - `first_use_at`     — wall-clock of the most-recent "first" hit
 *   - `count_in_window`  — number of hits inside that 10-min bucket
 *   - `lockout_until`    — wall-clock deadline of the post-3rd 5-min lockout
 *   - `watchful_until`   — wall-clock deadline of the 10-min watchful window
 *   - `ban_until`        — wall-clock deadline of the 1-hour ban
 *
 * Wall-clock is intentional: changing system date doesn't bypass the gate
 * because going BACKWARDS in time still satisfies `now < X_until` and going
 * forwards only burns the window earlier, which is no help to a fraudster.
 *
 * Scope: this counter is GLOBAL across all bound apps. Selecting "臨時用"
 * while opening 網銀 A and then later opening 網銀 B both feed the same
 * 10-min bucket — switching launcher apps does NOT reset the counter.
 */
@Singleton
class TempUseTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AntiScamClock
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class Stage { FIRST, SECOND, THIRD, LOCKED_OUT, BANNED }

    data class Snapshot(
        val stage: Stage,
        val countInWindow: Int,
        val windowEndsAt: Long,
        val lockoutUntil: Long,
        /** Non-zero while the post-lockout 10-min watchful window is active. */
        val watchfulUntil: Long,
        /** Non-zero while the 1-hour ban is active. */
        val banUntil: Long
    )

    fun snapshot(): Snapshot {
        val now = clock.now()

        // Ban beats every other state — it's the terminal punishment tier.
        val banUntil = prefs.getLong(KEY_BAN_UNTIL, 0L)
        if (banUntil != 0L && now < banUntil) {
            return Snapshot(Stage.BANNED, 0, 0L, 0L, 0L, banUntil)
        }
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil != 0L && now < lockoutUntil) {
            return Snapshot(Stage.LOCKED_OUT, 0, 0L, lockoutUntil, 0L, 0L)
        }

        val watchfulUntilRaw = prefs.getLong(KEY_WATCHFUL_UNTIL, 0L)
        val watchfulActive = watchfulUntilRaw != 0L && now < watchfulUntilRaw

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
            lockoutUntil = 0L,
            watchfulUntil = if (watchfulActive) watchfulUntilRaw else 0L,
            banUntil = 0L
        )
    }

    /**
     * Commit one "臨時用" tap. Returns the *resulting* stage the UI should
     * show. Reaching THIRD has two outcomes depending on prior state:
     *  - watchful inactive → 5-min lockout (existing behavior)
     *  - watchful active   → 1-hour ban (escalation tier)
     */
    fun consume(): Stage {
        val now = clock.now()
        val current = snapshot()
        if (current.stage == Stage.LOCKED_OUT) return Stage.LOCKED_OUT
        if (current.stage == Stage.BANNED) return Stage.BANNED

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
            if (current.watchfulUntil > now) {
                // Escalation: 3rd warning during watchful window → 1-hour ban.
                // Clear the watchful flag — the ban replaces it as the active
                // terminal state.
                editor.putLong(KEY_BAN_UNTIL, now + BAN_MS)
                editor.remove(KEY_WATCHFUL_UNTIL)
            } else {
                editor.putLong(KEY_LOCKOUT_UNTIL, now + LOCKOUT_MS)
            }
        }
        editor.apply()
        return if (stage == Stage.THIRD && current.watchfulUntil > now) Stage.BANNED else stage
    }

    /**
     * Idempotent state-transition sweeper. Safe to call from any 1-sec tick.
     * Drives the LOCKED_OUT → WATCHFUL → reset and BANNED → reset transitions
     * without requiring an explicit "elapsed" event from the UI.
     */
    fun clearLockoutIfElapsed() {
        val now = clock.now()

        val banUntil = prefs.getLong(KEY_BAN_UNTIL, 0L)
        if (banUntil != 0L && now >= banUntil) {
            prefs.edit().clear().apply()
            return
        }

        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil != 0L && now >= lockoutUntil) {
            // 5-min lockout elapsed → enter the 10-min watchful period.
            // Counter resets so the user starts a fresh 1→2→3 climb; but the
            // watchful flag means the next THIRD escalates straight to BAN.
            val watchfulUntil = lockoutUntil + WATCHFUL_MS
            prefs.edit()
                .remove(KEY_LOCKOUT_UNTIL)
                .remove(KEY_FIRST_USE_AT)
                .remove(KEY_COUNT)
                .putLong(KEY_WATCHFUL_UNTIL, watchfulUntil)
                .apply()
            return
        }

        val watchfulUntil = prefs.getLong(KEY_WATCHFUL_UNTIL, 0L)
        if (watchfulUntil != 0L && now >= watchfulUntil) {
            // Watchful window survived without a re-offense → silent reset.
            prefs.edit().remove(KEY_WATCHFUL_UNTIL).apply()
        }
    }

    /** Test / debug only. */
    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val WINDOW_MS: Long = 10L * 60 * 1000
        const val LOCKOUT_MS: Long = 5L * 60 * 1000
        const val WATCHFUL_MS: Long = 10L * 60 * 1000
        const val BAN_MS: Long = 60L * 60 * 1000

        private const val PREFS_NAME = "temp_use_tracker"
        private const val KEY_FIRST_USE_AT = "first_use_at"
        private const val KEY_COUNT = "count"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_WATCHFUL_UNTIL = "watchful_until"
        private const val KEY_BAN_UNTIL = "ban_until"
    }
}
