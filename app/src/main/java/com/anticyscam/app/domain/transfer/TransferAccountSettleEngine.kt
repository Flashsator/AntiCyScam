package com.anticyscam.app.domain.transfer

import com.anticyscam.app.data.local.entity.TransferAccountEntity
import com.anticyscam.app.data.prefs.NowSnapshot
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.domain.model.TransferAccountState

/**
 * Pure-function settlement for [TransferAccountEntity] cooldowns.
 *
 * Identical clock-tamper rule to
 * [com.anticyscam.app.domain.binding.BindingSettleEngine]:
 *
 *   add = max(0, min(wallDelta, elapsedDelta))
 *
 * meaning a fast-forwarded system clock cannot accelerate a timer (elapsed
 * bounds it) and a rewound clock cannot extend one (wall delta is non-
 * negative). After reboot, [bootSettle] falls back to wall progress with a
 * 24h-per-boot cap so a power-off + clock-forward + power-on cycle is
 * limited to one day of stolen progress.
 *
 * The built-in 「臨時用」 row (`isDefault = true`) is exempted at every
 * entry point — it never matures, never cools down, never auto-deletes.
 * Per user spec: 「臨時用是永遠長駐的臨時轉帳功能，那個計時什麼的都與他無關」.
 */
object TransferAccountSettleEngine {

    /** Maturation gate — the 「已綁定」 threshold. */
    const val MATURATION_MS: Long = 24L * 60L * 60L * 1000L

    /** Cooldown after a matured / non-matured row's request-delete. */
    const val DELETE_COOLDOWN_MS: Long = 48L * 60L * 60L * 1000L

    /**
     * Cap per-boot wall progress so 「關機 → 把時間調快一年 → 開機」 cannot
     * instantly mature or auto-delete a row.
     */
    const val MAX_BOOT_WALL_DELTA_MS: Long = 24L * 60L * 60L * 1000L

    private const val NANOS_PER_MILLI: Long = 1_000_000L

    /**
     * Normal-path settle. Returns a new entity with anchors + accumulated
     * millis advanced — never mutates the input. Default rows pass through
     * unchanged.
     */
    fun settle(row: TransferAccountEntity, now: NowSnapshot): TransferAccountEntity {
        if (row.isDefault) return row
        val advanced = monotonicAdvanceMs(row, now)
        return applyAdvance(row, now, advanced)
    }

    /**
     * Boot-recovery settle. Monotonic anchors are stale across boot, so we
     * fall back to wall progress capped at [MAX_BOOT_WALL_DELTA_MS]. Called
     * from [BootReceiver] on BOOT_COMPLETED.
     */
    fun bootSettle(
        row: TransferAccountEntity,
        now: NowSnapshot,
        maxBootDelta: Long = MAX_BOOT_WALL_DELTA_MS
    ): TransferAccountEntity {
        if (row.isDefault) return row
        val wallDelta = (now.wallMillis - row.lastSettledWall).coerceAtLeast(0L)
        val clamped = wallDelta.coerceAtMost(maxBootDelta)
        return applyAdvance(row, now, clamped)
    }

    /**
     * UI-facing state at this instant — pure, never mutates. Default rows
     * always return [TransferAccountState.Default]. If accumulated delete
     * progress already crossed the 48h threshold, returns
     * [TransferAccountState.PendingDeletion] with remainingMs = 0; callers
     * are expected to hard-delete such rows on their next sweep.
     */
    fun deriveState(row: TransferAccountEntity, now: NowSnapshot): TransferAccountState {
        if (row.isDefault) return TransferAccountState.Default
        val advanced = monotonicAdvanceMs(row, now)
        val virtualBound = if (row.deleteRequestedAtWall == null) {
            row.accumulatedBoundMillis + advanced
        } else {
            row.accumulatedBoundMillis
        }
        val virtualDelete = if (row.deleteRequestedAtWall != null) {
            row.accumulatedDeleteMillis + advanced
        } else {
            row.accumulatedDeleteMillis
        }
        return classify(
            deleteRequestedAtWall = row.deleteRequestedAtWall,
            accumulatedBoundMillis = virtualBound,
            accumulatedDeleteMillis = virtualDelete
        )
    }

    /**
     * Domain-side overload — avoids the per-tick `TransferAccountEntity`
     * allocation that the repository would otherwise need just to call into
     * [deriveState]. Pure read of the same six fields the entity overload
     * uses. See memory: scroll-perf-rule.
     */
    fun deriveState(account: TransferAccount, now: NowSnapshot): TransferAccountState {
        if (account.isDefault) return TransferAccountState.Default
        val advanced = monotonicAdvanceMs(
            lastSettledWall = account.lastSettledWall,
            lastSettledElapsedNanos = account.lastSettledElapsedNanos,
            now = now
        )
        val virtualBound = if (account.deleteRequestedAtWall == null) {
            account.accumulatedBoundMillis + advanced
        } else {
            account.accumulatedBoundMillis
        }
        val virtualDelete = if (account.deleteRequestedAtWall != null) {
            account.accumulatedDeleteMillis + advanced
        } else {
            account.accumulatedDeleteMillis
        }
        return classify(
            deleteRequestedAtWall = account.deleteRequestedAtWall,
            accumulatedBoundMillis = virtualBound,
            accumulatedDeleteMillis = virtualDelete
        )
    }

    /** True iff settlement says the row's auto-delete cooldown has expired. */
    fun isAutoDeleteDue(row: TransferAccountEntity, now: NowSnapshot): Boolean {
        if (row.isDefault || row.deleteRequestedAtWall == null) return false
        val advanced = monotonicAdvanceMs(row, now)
        return row.accumulatedDeleteMillis + advanced >= DELETE_COOLDOWN_MS
    }

    private fun classify(
        deleteRequestedAtWall: Long?,
        accumulatedBoundMillis: Long,
        accumulatedDeleteMillis: Long
    ): TransferAccountState {
        if (deleteRequestedAtWall != null) {
            val remaining =
                (DELETE_COOLDOWN_MS - accumulatedDeleteMillis).coerceAtLeast(0L)
            return TransferAccountState.PendingDeletion(remaining)
        }
        if (accumulatedBoundMillis >= MATURATION_MS) return TransferAccountState.Matured
        val remaining = (MATURATION_MS - accumulatedBoundMillis).coerceAtLeast(0L)
        return TransferAccountState.PendingMaturation(remaining)
    }

    /**
     * How much millis to add since the last settle, using
     * min(wallDelta, elapsedDelta) so neither clock alone can be cheated.
     * Returns 0 if either anchor is "unset" (0) — that means the row was
     * freshly inserted / migrated and there is no history to advance.
     */
    private fun monotonicAdvanceMs(
        row: TransferAccountEntity,
        now: NowSnapshot
    ): Long = monotonicAdvanceMs(
        lastSettledWall = row.lastSettledWall,
        lastSettledElapsedNanos = row.lastSettledElapsedNanos,
        now = now
    )

    private fun monotonicAdvanceMs(
        lastSettledWall: Long,
        lastSettledElapsedNanos: Long,
        now: NowSnapshot
    ): Long {
        if (lastSettledWall == 0L || lastSettledElapsedNanos == 0L) return 0L
        val wallDeltaMs = now.wallMillis - lastSettledWall
        val elapsedDeltaMs =
            (now.elapsedNanos - lastSettledElapsedNanos) / NANOS_PER_MILLI
        return maxOf(0L, minOf(wallDeltaMs, elapsedDeltaMs))
    }

    private fun applyAdvance(
        row: TransferAccountEntity,
        now: NowSnapshot,
        advanceMs: Long
    ): TransferAccountEntity {
        val isCoolingDown = row.deleteRequestedAtWall != null
        val newAccumulatedBound = if (!isCoolingDown) {
            (row.accumulatedBoundMillis + advanceMs).coerceAtMost(MATURATION_MS)
        } else {
            row.accumulatedBoundMillis
        }
        val newAccumulatedDelete = if (isCoolingDown) {
            (row.accumulatedDeleteMillis + advanceMs).coerceAtMost(DELETE_COOLDOWN_MS)
        } else {
            row.accumulatedDeleteMillis
        }
        return row.copy(
            accumulatedBoundMillis = newAccumulatedBound,
            accumulatedDeleteMillis = newAccumulatedDelete,
            lastSettledWall = now.wallMillis,
            lastSettledElapsedNanos = now.elapsedNanos
        )
    }
}
