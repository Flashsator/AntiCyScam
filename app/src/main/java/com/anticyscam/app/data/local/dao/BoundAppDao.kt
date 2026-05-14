package com.anticyscam.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anticyscam.app.data.local.entity.BoundAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BoundAppDao {

    @Query("SELECT * FROM bound_apps ORDER BY bound_at ASC")
    fun observeAll(): Flow<List<BoundAppEntity>>

    @Query("SELECT * FROM bound_apps")
    suspend fun all(): List<BoundAppEntity>

    @Query("SELECT package_name FROM bound_apps")
    suspend fun allPackageNames(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM bound_apps WHERE package_name = :pkg)")
    suspend fun isBound(pkg: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<BoundAppEntity>)

    /**
     * Used by the settle engine to write back accumulated millis without
     * touching label/boundAt. Caller must pass the full entity — Room's
     * @Update replaces all columns.
     */
    @Update
    suspend fun update(row: BoundAppEntity)

    @Update
    suspend fun updateAll(rows: List<BoundAppEntity>)

    @Query("DELETE FROM bound_apps WHERE package_name = :pkg")
    suspend fun deleteByPackage(pkg: String)

    @Query("DELETE FROM bound_apps WHERE package_name IN (:pkgs)")
    suspend fun deleteByPackages(pkgs: List<String>)

    @Query("DELETE FROM bound_apps")
    suspend fun clear()
}
