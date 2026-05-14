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

    @Query(
        "UPDATE transfer_accounts SET last_used_at = :ts, dormant_consumed = 0 " +
            "WHERE id = :id AND is_default = 0"
    )
    suspend fun touchUsage(id: Long, ts: Long)

    @Query(
        "UPDATE transfer_accounts SET dormant_consumed = 1 WHERE id = :id AND is_default = 0"
    )
    suspend fun markDormantConsumed(id: Long)

    /**
     * Atomically replace the encrypted account number AND decrement the
     * one-shot edit slot. The `edits_remaining > 0` guard means a caller
     * who races past the repository gate cannot drain the slot below 0.
     * Returns rows affected — 0 means the edit slot was already exhausted.
     */
    @Query(
        "UPDATE transfer_accounts SET " +
            "account_cipher = :cipher, " +
            "created_at = :createdAt, " +
            "cooldown_ends_at = :cooldownEndsAt, " +
            "cooldown_open_target = :openTarget, " +
            "edits_remaining = edits_remaining - 1, " +
            "dormant_consumed = 0 " +
            "WHERE id = :id AND is_default = 0 AND edits_remaining > 0"
    )
    suspend fun replaceAccountCipher(
        id: Long,
        cipher: String,
        createdAt: Long,
        cooldownEndsAt: Long,
        openTarget: Int
    ): Int

    @Query("UPDATE transfer_accounts SET name = :name WHERE id = :id AND is_default = 0")
    suspend fun rename(id: Long, name: String)

    /**
     * Decrement edits_remaining without touching the encrypted number.
     * Used when an edit changes only the name (label) — the edit slot is
     * still consumed because the PRD models "編輯一次" as one slot total,
     * not one per field.
     */
    @Query(
        "UPDATE transfer_accounts SET edits_remaining = edits_remaining - 1 " +
            "WHERE id = :id AND is_default = 0 AND edits_remaining > 0"
    )
    suspend fun decrementEditsRemaining(id: Long): Int

    /**
     * Wipe only user-created accounts. The built-in 「臨時用」default row
     * (is_default = 1) is preserved across "clear all data" by design —
     * it is part of the app's fixture, not user data, and the gate flow
     * depends on its always-present id.
     */
    @Query("DELETE FROM transfer_accounts WHERE is_default = 0")
    suspend fun clearUserCreated()
}
