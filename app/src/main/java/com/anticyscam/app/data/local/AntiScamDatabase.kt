package com.anticyscam.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anticyscam.app.data.local.dao.BoundAppDao
import com.anticyscam.app.data.local.dao.TransferAccountDao
import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.data.local.entity.TransferAccountEntity

@Database(
    entities = [TransferAccountEntity::class, BoundAppEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AntiScamDatabase : RoomDatabase() {
    abstract fun transferAccountDao(): TransferAccountDao
    abstract fun boundAppDao(): BoundAppDao

    companion object {
        const val DB_NAME = "anticyscam.db"

        // v2 → v3: add edits_remaining column for the one-shot edit slot.
        // Existing rows default to 1 so accounts created before this version
        // get exactly one edit, same as freshly added rows.
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transfer_accounts " +
                        "ADD COLUMN edits_remaining INTEGER NOT NULL DEFAULT 1"
                )
            }
        }
    }
}
