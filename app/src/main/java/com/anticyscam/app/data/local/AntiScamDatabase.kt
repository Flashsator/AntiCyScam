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
    version = 6,
    exportSchema = false
)
abstract class AntiScamDatabase : RoomDatabase() {
    abstract fun transferAccountDao(): TransferAccountDao
    abstract fun boundAppDao(): BoundAppDao

    companion object {
        const val DB_NAME = "anticyscam.db"

        // v2 → v3: add edits_remaining column for the one-shot edit slot.
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transfer_accounts " +
                        "ADD COLUMN edits_remaining INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        // v3 → v4: bind-maturation + cooldown-unbind columns on bound_apps.
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

        // v4 → v5: rebuild transfer_accounts with dual-anchor maturation +
        // delete-cooldown columns and drop the legacy cooldown_ends_at /
        // cooldown_open_target / last_used_at / dormant_consumed columns
        // (Plan v5 replaced wall-only cooldown with the settle-engine model
        //  and removed the 90-day Dormant branch).
        //
        // SQLite has no in-place DROP COLUMN that preserves indexes here, so
        // we recreate the table. Existing rows carry forward as "freshly
        // matured at migration time" — accumulated_bound_millis starts at 0
        // and the next settle picks it up from boundAnchorWall = createdAt.
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE transfer_accounts_new (
                      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      name TEXT NOT NULL,
                      account_cipher TEXT NOT NULL,
                      is_default INTEGER NOT NULL DEFAULT 0,
                      created_at INTEGER NOT NULL,
                      edits_remaining INTEGER NOT NULL DEFAULT 1,
                      bound_anchor_wall INTEGER NOT NULL DEFAULT 0,
                      bound_anchor_elapsed_nanos INTEGER NOT NULL DEFAULT 0,
                      accumulated_bound_millis INTEGER NOT NULL DEFAULT 0,
                      delete_requested_at_wall INTEGER,
                      delete_requested_at_elapsed_nanos INTEGER,
                      accumulated_delete_millis INTEGER NOT NULL DEFAULT 0,
                      last_settled_wall INTEGER NOT NULL DEFAULT 0,
                      last_settled_elapsed_nanos INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO transfer_accounts_new (
                      id, name, account_cipher, is_default, created_at, edits_remaining,
                      bound_anchor_wall, bound_anchor_elapsed_nanos, accumulated_bound_millis,
                      delete_requested_at_wall, delete_requested_at_elapsed_nanos,
                      accumulated_delete_millis, last_settled_wall, last_settled_elapsed_nanos
                    )
                    SELECT
                      id, name, account_cipher, is_default, created_at, edits_remaining,
                      created_at, 0, 0,
                      NULL, NULL,
                      0, 0, 0
                    FROM transfer_accounts
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE transfer_accounts")
                db.execSQL(
                    "ALTER TABLE transfer_accounts_new RENAME TO transfer_accounts"
                )
            }
        }

        // v5 → v6: add optional bank_code column to transfer_accounts.
        // Nullable TEXT — older rows survive with NULL, new rows can leave
        // it null/blank when the user omits the "銀行代碼(選填)" field.
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transfer_accounts ADD COLUMN bank_code TEXT"
                )
            }
        }
    }
}
