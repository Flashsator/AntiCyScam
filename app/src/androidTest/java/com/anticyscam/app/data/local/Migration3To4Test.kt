package com.anticyscam.app.data.local

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the v3 → v4 Room migration.
 *
 * Why instrumented (not JVM): the raw SQL path needs a real SQLite engine,
 * and Robolectric is not in our dependency set. Using AndroidJUnit4 +
 * [FrameworkSQLiteOpenHelperFactory] keeps this test self-contained — no
 * Room schema export is required because we apply [AntiScamDatabase.MIGRATION_3_4]
 * directly to a hand-crafted v3 schema.
 *
 * Invariants verified:
 *   1. All 7 new columns exist after migration with the documented types.
 *   2. The existing row's package_name/label/bound_at survive the migration.
 *   3. Counter columns default to 0 for pre-existing rows.
 *   4. Unbind nullable columns default to NULL (i.e. no cooldown active).
 */
@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    private val testDbName = "migration-test.db"

    private val v3SchemaSql = """
        CREATE TABLE IF NOT EXISTS bound_apps (
            package_name TEXT NOT NULL PRIMARY KEY,
            label TEXT NOT NULL,
            bound_at INTEGER NOT NULL
        )
    """.trimIndent()

    private fun openHelper(name: String, version: Int): SupportSQLiteOpenHelper {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(v3SchemaSql)
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @After
    fun cleanup() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .deleteDatabase(testDbName)
    }

    @Test
    fun migration_3_to_4_adds_columns_and_preserves_existing_row() {
        // Arrange — create v3 DB with a single row.
        val helper = openHelper(testDbName, 3)
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO bound_apps (package_name, label, bound_at) " +
                    "VALUES ('com.example.bank', 'Bank', 123456789)"
            )
        }
        helper.close()

        // Act — open the same file via a v4 helper that simply opens (no
        // recreate) and then apply the production migration manually.
        val raw = openHelper(testDbName, 3)
        raw.writableDatabase.use { db ->
            AntiScamDatabase.MIGRATION_3_4.migrate(db)

            // Assert — schema introspection via PRAGMA table_info.
            val cols = mutableMapOf<String, ColumnInfo>()
            db.query("PRAGMA table_info(bound_apps)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                val typeIdx = c.getColumnIndex("type")
                val notnullIdx = c.getColumnIndex("notnull")
                while (c.moveToNext()) {
                    cols[c.getString(nameIdx)] = ColumnInfo(
                        type = c.getString(typeIdx),
                        notNull = c.getInt(notnullIdx) == 1
                    )
                }
            }

            // Original v3 columns.
            assertTrue("package_name missing", "package_name" in cols)
            assertTrue("label missing", "label" in cols)
            assertTrue("bound_at missing", "bound_at" in cols)

            // 7 new columns expected.
            val expectedNew = listOf(
                "bound_at_elapsed_nanos" to true,
                "accumulated_bound_millis" to true,
                "last_settled_wall" to true,
                "last_settled_elapsed_nanos" to true,
                "unbind_requested_at_wall" to false,
                "unbind_requested_at_elapsed_nanos" to false,
                "accumulated_unbind_millis" to true
            )
            expectedNew.forEach { (name, expectNotNull) ->
                val info = cols[name]
                assertNotNull("column $name missing after migration", info)
                assertEquals(
                    "column $name should be INTEGER",
                    "INTEGER",
                    info!!.type.uppercase()
                )
                assertEquals(
                    "column $name notnull mismatch",
                    expectNotNull,
                    info.notNull
                )
            }

            // Row preserved + defaults applied.
            db.query("SELECT * FROM bound_apps WHERE package_name = 'com.example.bank'")
                .use { c ->
                    assertTrue("row missing after migration", c.moveToNext())
                    assertEquals(
                        "Bank",
                        c.getString(c.getColumnIndexOrThrow("label"))
                    )
                    assertEquals(
                        123456789L,
                        c.getLong(c.getColumnIndexOrThrow("bound_at"))
                    )
                    assertEquals(
                        0L,
                        c.getLong(c.getColumnIndexOrThrow("bound_at_elapsed_nanos"))
                    )
                    assertEquals(
                        0L,
                        c.getLong(c.getColumnIndexOrThrow("accumulated_bound_millis"))
                    )
                    assertEquals(
                        0L,
                        c.getLong(c.getColumnIndexOrThrow("last_settled_wall"))
                    )
                    assertEquals(
                        0L,
                        c.getLong(c.getColumnIndexOrThrow("accumulated_unbind_millis"))
                    )
                    val unbindWallIdx =
                        c.getColumnIndexOrThrow("unbind_requested_at_wall")
                    assertTrue(
                        "unbind_requested_at_wall should be NULL for migrated row",
                        c.isNull(unbindWallIdx)
                    )
                    val unbindElapsedIdx =
                        c.getColumnIndexOrThrow("unbind_requested_at_elapsed_nanos")
                    assertTrue(
                        "unbind_requested_at_elapsed_nanos should be NULL for migrated row",
                        c.isNull(unbindElapsedIdx)
                    )
                }
        }
    }

    private data class ColumnInfo(val type: String, val notNull: Boolean)
}
