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
    version = 4,
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

        // v3 → v4: bind-maturation + cooldown-unbind columns.
        // Existing rows are treated as "freshly bound at the migration moment"
        // by leaving the elapsed-nanos and accumulated values at 0 — the
        // settle engine will start counting from the next snapshot. Wall is
        // already known via the existing bound_at column.
        // Unbind columns are nullable so NULL means "no cooldown active".
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE bound_apps " +
                        "ADD COLUMN bound_at_elapsed_nanos INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE bound_apps " +
                        "ADD COLUMN accumulated_bound_millis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE bound_apps " +
                        "ADD COLUMN last_settled_wall INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE bound_apps " +
                        "ADD COLUMN last_settled_elapsed_nanos INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE bound_apps " +
                        "ADD COLUMN unbind_requested_at_wall INTEGER DEFAULT NULL"
                )
                db.execSQL(
                    "ALTER TABLE bound_apps " +
                        "ADD COLUMN unbind_requested_at_elapsed_nanos INTEGER DEFAULT NULL"
                )
                db.execSQL(
                    "ALTER TABLE bound_apps " +
                        "ADD COLUMN accumulated_unbind_millis INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
