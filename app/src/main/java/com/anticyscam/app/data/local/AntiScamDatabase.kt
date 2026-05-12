package com.anticyscam.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.anticyscam.app.data.local.dao.BoundAppDao
import com.anticyscam.app.data.local.dao.TransferAccountDao
import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.data.local.entity.TransferAccountEntity

@Database(
    entities = [TransferAccountEntity::class, BoundAppEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AntiScamDatabase : RoomDatabase() {
    abstract fun transferAccountDao(): TransferAccountDao
    abstract fun boundAppDao(): BoundAppDao

    companion object {
        const val DB_NAME = "anticyscam.db"
    }
}
