package com.anticyscam.app.domain.binding

import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.data.prefs.NowSnapshot
import com.anticyscam.app.domain.model.BindingState

/**
 * Pure-function settlement for [BoundAppEntity] cooldowns.
 *
 * Why two clocks (wall + monotonic):
 *   - Wall time can jump forward (user fast-forwards system clock to
 *     accelerate the timer) or backward (user rewinds to extend it).
 *   - Monotonic time (elapsedRealtimeNanos) cannot be set by the user; it
 *     only resets on reboot.
 *
 * Settlement rule:
 *   add = max(0, min(wallDelta, elapsedDelta))
 *
 *   - If user rewinds wall   → wallDelta negative → add = 0  (no extension)
 *   - If user fast-forwards  → elapsedDelta bounds it           (no shortening)
 *   - Normal case            → both deltas equal, both used
 *   - After reboot           → elapsedDelta is "since boot only"; we fall
 *     back to wall in [bootSettle] with a 24h clamp so a power-off + clock
 *     forward + power-on attack can only steal at most 24h per boot.
 *
 * NB: deltas are clamped to non-negative because a NULL anchor (newly
 * inserted row, or migrated row) reads as 0, so the first settle after that
 * looks like a huge wall jump from epoch — we ignore it and start counting
 * from "now" instead.
 */
object BindingSettleEngine {

    /** Maturation gate — the "已綁定" threshold. */
    const val MATURATION_MS: Long = 24L * 60L * 60L * 1000L

    /** Cooldown after a matured-app unbind request. */
    const val UNBIND_COOLDOWN_MS: Long = 48L * 60L * 60L * 1000L

    /**
     * Cap per-boot wall progress so "power-off, set clock +1 year, power-on"
     * cannot instantly fully unlock or auto-unbind a row.
     */
    const val MAX_BOOT_WALL_DELTA_MS: Long = 24L * 60L * 60L * 1000L

    private const val NANOS_PER_MILLI: Long = 1_000_000L

    /**
     * Normal-path settle: row was alive during the period between last
     * settle and `now`. Both anchors are valid (set on insert or by the
     * previous settle).
     *
     * Returns a copy of [row] with accumulated millis advanced + last-settled
     * anchors updated. If neither cooldown is active (UNBOUND-equivalent
     * after maturation reached MATURATION_MS and no unbind requested), only
     * the anchor moves.
     */
    fun settle(row: BoundAppEntity, now: NowSnapshot): BoundAppEntity {
        val advanced = monotonicAdvanceMs(row, now)
        return applyAdvance(row, now, advanced)
    }

    /**
     * Boot-recovery settle: monotonic clock has reset, so [row.lastSettledElapsedNanos]
     * is from a different boot session and useless. We fall back to wall
     * progress with [MAX_BOOT_WALL_DELTA_MS] clamping.
     *
     * Called from [BootReceiver] on BOOT_COMPLETED.
     */
    fun bootSettle(
        row: BoundAppEntity,
        now: NowSnapshot,
        maxBootDelta: Long = MAX_BOOT_WALL_DELTA_MS
    ): BoundAppEntity {
        val wallDelta = (now.wallMillis - row.lastSettledWall).coerceAtLeast(0L)
        val clamped = wallDelta.coerceAtMost(maxBootDelta)
        return applyAdvance(row, now, clamped)
    }

    /**
     * Derive the UI-facing state at this instant. Does NOT mutate the row.
     * If the row's progress already crossed the 48h cooldown threshold, the
     * state is [BindingState.Unbound] — callers should hard-delete the row.
     */
    fun deriveState(row: BoundAppEntity, now: NowSnapshot): BindingState {
        // Compute *virtual* accumulated values without writing them, so the
        // UI can update every second without a DB write per tick.
        val advanced = monotonicAdvanceMs(row, now)
        return classify(
            unbindRequestedAtWall = row.unbindRequestedAtWall,
            accumulatedBoundMillis = if (row.unbindRequestedAtWall == null) {
                row.accumulatedBoundMillis + advanced
            } else {
                row.accumulatedBoundMillis
            },
            accumulatedUnbindMillis = if (row.unbindRequestedAtWall != null) {
                row.accumulatedUnbindMillis + advanced
            } else {
                row.accumulatedUnbindMillis
            }
        )
    }

    private fun classify(
        unbindRequestedAtWall: Long?,
        accumulatedBoundMillis: Long,
        accumulatedUnbindMillis: Long
    ): BindingState {
        if (unbindRequestedAtWall != null) {
            val remaining = (UNBIND_COOLDOWN_MS - accumulatedUnbindMillis).coerceAtLeast(0L)
            return if (remaining == 0L) BindingState.Unbound
            else BindingState.PendingUnbind(remaining)
        }
        if (accumulatedBoundMillis >= MATURATION_MS) return BindingState.Matured
        val remaining = (MATURATION_MS - accumulatedBoundMillis).coerceAtLeast(0L)
        return BindingState.PendingMaturation(remaining)
    }

    /**
     * How much millis to add since the last settle, using
     * min(wallDelta, elapsedDelta) so neither clock alone can be cheated.
     * Returns 0 if either anchor is "unset" (0).
     */
    private fun monotonicAdvanceMs(row: BoundAppEntity, now: NowSnapshot): Long {
        if (row.lastSettledWall == 0L || row.lastSettledElapsedNanos == 0L) {
            // First settle since insert/migration — no history to advance.
            return 0L
        }
        val wallDeltaMs = now.wallMillis - row.lastSettledWall
        val elapsedDeltaMs =
            (now.elapsedNanos - row.lastSettledElapsedNanos) / NANOS_PER_MILLI
        return maxOf(0L, minOf(wallDeltaMs, elapsedDeltaMs))
    }

    /**
     * Apply [advanceMs] to whichever counter is active, then refresh the
     * settle anchors. Returns a new row — never mutates the input.
     */
    private fun applyAdvance(
        row: BoundAppEntity,
        now: NowSnapshot,
        advanceMs: Long
    ): BoundAppEntity {
        val isCoolingDown = row.unbindRequestedAtWall != null
        val newAccumulatedBound = if (!isCoolingDown) {
            (row.accumulatedBoundMillis + advanceMs).coerceAtMost(MATURATION_MS)
        } else {
            row.accumulatedBoundMillis
        }
        val newAccumulatedUnbind = if (isCoolingDown) {
            (row.accumulatedUnbindMillis + advanceMs).coerceAtMost(UNBIND_COOLDOWN_MS)
        } else {
            row.accumulatedUnbindMillis
        }
        return row.copy(
            accumulatedBoundMillis = newAccumulatedBound,
            accumulatedUnbindMillis = newAccumulatedUnbind,
            lastSettledWall = now.wallMillis,
            lastSettledElapsedNanos = now.elapsedNanos
        )
    }
}
