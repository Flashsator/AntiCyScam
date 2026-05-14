package com.anticyscam.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anticyscam.app.domain.model.TransferAccount

/**
 * Persistence form of a transfer account.
 *
 * The account number is stored encrypted (base64 of AES-GCM ciphertext)
 * via [com.anticyscam.app.data.crypto.FieldCipher]. The repository layer
 * handles encryption / decryption so callers see plain Strings.
 *
 * [isDefault] = true marks the built-in 「臨時用」 entry which is created on
 * first run and is exempt from every cooldown column — see
 * [com.anticyscam.app.domain.transfer.TransferAccountSettleEngine].
 *
 * Cooldown columns (v5) mirror [BoundAppEntity]'s dual-anchor design so the
 * settle engine can clamp progress to `min(wallDelta, elapsedDelta)` — a
 * fast-forwarded system clock cannot shorten the timer.
 *
 *   PendingMaturation (0–24h)
 *     accumulated_bound_millis < 24h ; delete_requested_at_wall IS NULL
 *   Matured (>= 24h accumulated)
 *     accumulated_bound_millis >= 24h ; delete_requested_at_wall IS NULL
 *   PendingDeletion (0–48h after request)
 *     delete_requested_at_wall IS NOT NULL ; accumulated_delete_millis < 48h
 *   Auto-deleted
 *     row hard-removed when accumulated_delete_millis >= 48h
 */
@Entity(tableName = "transfer_accounts")
data class TransferAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "account_cipher")
    val accountCipher: String,

    /**
     * Optional Taiwan bank code (台幣三碼). Stored in plaintext — the bank
     * code is not user-sensitive in the same way the account number is, and
     * keeping it queryable is more useful than encrypting it. Nullable so
     * pre-v6 rows survive migration without a default.
     */
    @ColumnInfo(name = "bank_code")
    val bankCode: String? = null,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "edits_remaining")
    val editsRemaining: Int = TransferAccount.INITIAL_EDITS_REMAINING,

    /** Wall-clock anchor at maturation-timer start (= createdAt at insert). */
    @ColumnInfo(name = "bound_anchor_wall")
    val boundAnchorWall: Long = 0L,

    /** Monotonic anchor paired with [boundAnchorWall]. */
    @ColumnInfo(name = "bound_anchor_elapsed_nanos")
    val boundAnchorElapsedNanos: Long = 0L,

    /** Total millis accumulated toward the 24h maturation gate. */
    @ColumnInfo(name = "accumulated_bound_millis")
    val accumulatedBoundMillis: Long = 0L,

    /** NULL = no delete requested; non-NULL = cooldown is running. */
    @ColumnInfo(name = "delete_requested_at_wall")
    val deleteRequestedAtWall: Long? = null,

    /** Monotonic anchor at delete-request time. */
    @ColumnInfo(name = "delete_requested_at_elapsed_nanos")
    val deleteRequestedAtElapsedNanos: Long? = null,

    /** Total millis accumulated toward the 48h auto-delete threshold. */
    @ColumnInfo(name = "accumulated_delete_millis")
    val accumulatedDeleteMillis: Long = 0L,

    /** Last wall-time the settle engine wrote to this row. */
    @ColumnInfo(name = "last_settled_wall")
    val lastSettledWall: Long = 0L,

    /** Last monotonic-time the settle engine wrote to this row. */
    @ColumnInfo(name = "last_settled_elapsed_nanos")
    val lastSettledElapsedNanos: Long = 0L
)
