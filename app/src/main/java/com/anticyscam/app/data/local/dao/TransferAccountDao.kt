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

    @Query("DELETE FROM transfer_accounts")
    suspend fun clear()
}
