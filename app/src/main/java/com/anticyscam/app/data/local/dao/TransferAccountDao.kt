package com.anticyscam.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anticyscam.app.data.local.entity.TransferAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferAccountDao {

    @Query("SELECT * FROM transfer_accounts ORDER BY is_default DESC, created_at ASC")
    fun observeAll(): Flow<List<TransferAccountEntity>>

    @Query("SELECT * FROM transfer_accounts WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TransferAccountEntity?

    @Query("SELECT * FROM transfer_accounts WHERE is_default = 0")
    suspend fun allNonDefault(): List<TransferAccountEntity>

    @Query("SELECT COUNT(*) FROM transfer_accounts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM transfer_accounts WHERE is_default = 1")
    suspend fun defaultCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: TransferAccountEntity): Long

    @Update
    suspend fun update(account: TransferAccountEntity)

    @Delete
    suspend fun delete(account: TransferAccountEntity)

    @Query("DELETE FROM transfer_accounts WHERE id = :id AND is_default = 0")
    suspend fun deleteIfNotDefault(id: Long): Int

    /**
     * Atomically replace the encrypted account number AND decrement the
     * one-shot edit slot AND reset all maturation anchors. The
     * `edits_remaining > 0` guard means a caller who races past the
     * repository gate cannot drain the slot below 0. Returns rows
     * affected — 0 means the edit slot was already exhausted.
     */
    @Query(
        "UPDATE transfer_accounts SET " +
            "account_cipher = :cipher, " +
            "bank_code = :bankCode, " +
            "created_at = :nowWall, " +
            "bound_anchor_wall = :nowWall, " +
            "bound_anchor_elapsed_nanos = :nowElapsedNanos, " +
            "accumulated_bound_millis = 0, " +
            "delete_requested_at_wall = NULL, " +
            "delete_requested_at_elapsed_nanos = NULL, " +
            "accumulated_delete_millis = 0, " +
            "last_settled_wall = :nowWall, " +
            "last_settled_elapsed_nanos = :nowElapsedNanos, " +
            "edits_remaining = edits_remaining - 1 " +
            "WHERE id = :id AND is_default = 0 AND edits_remaining > 0"
    )
    suspend fun replaceAccountCipher(
        id: Long,
        cipher: String,
        bankCode: String?,
        nowWall: Long,
        nowElapsedNanos: Long
    ): Int

    /**
     * Name + bank-code edit that leaves the encrypted account number and the
     * maturation anchors untouched. Used by the editAccount path when only
     * the label or bank code changed (or nothing changed at all — the slot
     * is still burned via [decrementEditsRemaining]).
     */
    @Query(
        "UPDATE transfer_accounts SET name = :name, bank_code = :bankCode " +
            "WHERE id = :id AND is_default = 0"
    )
    suspend fun updateNameAndBankCode(id: Long, name: String, bankCode: String?)

    /**
     * Decrement edits_remaining without touching the encrypted number. Used
     * when an edit changes only the name (label) — the edit slot is still
     * consumed because "編輯一次" is one slot total, not one per field.
     */
    @Query(
        "UPDATE transfer_accounts SET edits_remaining = edits_remaining - 1 " +
            "WHERE id = :id AND is_default = 0 AND edits_remaining > 0"
    )
    suspend fun decrementEditsRemaining(id: Long): Int

    /**
     * Mark a row as PendingDeletion. The 48h cooldown anchors start running
     * from the supplied snapshot. The bound-maturation columns are left
     * intact so a cancel can return the row to its prior state untouched.
     * Guarded against the default row.
     */
    @Query(
        "UPDATE transfer_accounts SET " +
            "delete_requested_at_wall = :nowWall, " +
            "delete_requested_at_elapsed_nanos = :nowElapsedNanos, " +
            "accumulated_delete_millis = 0, " +
            "last_settled_wall = :nowWall, " +
            "last_settled_elapsed_nanos = :nowElapsedNanos " +
            "WHERE id = :id AND is_default = 0 AND delete_requested_at_wall IS NULL"
    )
    suspend fun requestDelete(
        id: Long,
        nowWall: Long,
        nowElapsedNanos: Long
    ): Int

    /**
     * Cancel a pending-delete and return the row to whatever
     * maturation state it was in. The maturation anchors are NOT touched —
     * settlement during the cooldown was applied to delete-millis, not
     * bound-millis, so the bound side is correct as-is.
     */
    @Query(
        "UPDATE transfer_accounts SET " +
            "delete_requested_at_wall = NULL, " +
            "delete_requested_at_elapsed_nanos = NULL, " +
            "accumulated_delete_millis = 0 " +
            "WHERE id = :id AND is_default = 0 AND delete_requested_at_wall IS NOT NULL"
    )
    suspend fun cancelDelete(id: Long): Int

    /**
     * Wipe only user-created accounts. The built-in 「臨時用」 default row
     * (is_default = 1) is preserved across "clear all data" by design —
     * it is part of the app's fixture, not user data.
     */
    @Query("DELETE FROM transfer_accounts WHERE is_default = 0")
    suspend fun clearUserCreated()
}
