package com.anticyscam.app.domain.model

/**
 * Domain representation of a transfer account.
 *
 * `accountNumber` is plain text at this layer — encryption is an
 * implementation detail of the data layer.
 *
 * Plan v5 replaces the previous 「cooldownEndsAt 純牆鐘」 model with the same
 * dual-anchor (wall + monotonic) settlement used by bound apps — see
 * [com.anticyscam.app.domain.transfer.TransferAccountSettleEngine] for the
 * full clock-tamper-resistance rules. The 90-day Dormant branch was removed
 * per user spec (「90 天未用拿掉好了，讓使用者自己決定刪除」).
 */
data class TransferAccount(
    val id: Long,
    val name: String,
    val accountNumber: String,
    val isDefault: Boolean,
    val createdAt: Long,

    /** Wall-clock anchor when the maturation timer began (= createdAt at insert). */
    val boundAnchorWall: Long,
    /** Monotonic anchor paired with [boundAnchorWall]. */
    val boundAnchorElapsedNanos: Long,
    /** Settled millis toward the 24h maturation gate. */
    val accumulatedBoundMillis: Long,

    /** NULL until the user requests delete; non-NULL = cooldown is running. */
    val deleteRequestedAtWall: Long?,
    /** Monotonic anchor at delete-request time. */
    val deleteRequestedAtElapsedNanos: Long?,
    /** Settled millis toward the 48h auto-delete threshold. */
    val accumulatedDeleteMillis: Long,

    /** Last wall-time the settle engine wrote to this row. */
    val lastSettledWall: Long,
    /** Last monotonic-time the settle engine wrote to this row. */
    val lastSettledElapsedNanos: Long,

    val editsRemaining: Int,

    /**
     * Optional bank code (e.g. 台幣三碼 "812"). Null/blank when the user did
     * not supply one. UI surfaces it as a "銀行代碼：XXX" line on the card
     * when present, and the AddTransferAccountDialog exposes it as the
     * 「銀行代碼(選填)」 field.
     */
    val bankCode: String? = null
) {
    companion object {
        /**
         * Hard ceiling on the total row count. Includes the永久 default
         * 「臨時用」 row, so users may add at most 5 of their own.
         */
        const val MAX_ACCOUNTS = 6

        /**
         * A freshly added account may be edited exactly once. After the edit
         * consumes this slot the only remaining action is request-delete,
         * making 「等使用者編輯時把帳號換成詐騙帳號」 attacks impossible past
         * the first commit.
         */
        const val INITIAL_EDITS_REMAINING: Int = 1
    }
}
