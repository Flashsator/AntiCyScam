package com.anticyscam.app.domain.model

/**
 * Runtime state of a transfer account.
 *
 * Mirrors [BindingState] but kept as a separate type because:
 *   - [Default] is a transfer-only branch (the built-in 「臨時用」 row that
 *     is permanent and clock-independent — no maturation, no deletion
 *     cooldown apply to it).
 *   - The "matured" copy and the "matured + delete-cooldown" combinations
 *     are conceptually distinct from a bound app's lifecycle, even though
 *     the timing constants are identical.
 *
 * State machine for a non-default row:
 *
 *   PendingMaturation (0–24h)
 *     → Matured           (cross 24h threshold)
 *     → user taps delete  → PendingDeletion (0–48h)
 *         → user cancels  → Matured
 *         → 48h elapses   → row hard-deleted (no terminal state surfaced)
 *
 *   Default rows never leave [Default].
 */
sealed interface TransferAccountState {
    /** Permanent built-in 「臨時用」 row. No timers apply. */
    data object Default : TransferAccountState

    /** Freshly added; [remainingMs] until the 24h threshold becomes 已綁定. */
    data class PendingMaturation(val remainingMs: Long) : TransferAccountState

    /** Past the 24h gate; the only available action is request-delete. */
    data object Matured : TransferAccountState

    /** Delete requested; [remainingMs] until the row is auto-deleted. */
    data class PendingDeletion(val remainingMs: Long) : TransferAccountState
}
