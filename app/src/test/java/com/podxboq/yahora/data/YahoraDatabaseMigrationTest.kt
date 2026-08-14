/*
 * Copyright (C) 2026 podxboq
 *
 * This file is part of Yahora.
 *
 * Yahora is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.podxboq.yahora.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * An installed app may already hold a version 1 database. Migrating must add
 * the entries table without discarding the tags the user already created.
 *
 * This builds a real version 1 database by hand and then opens it through Room,
 * which runs the migration and validates the resulting schema against the
 * exported one — an identity mismatch makes Room throw, failing this test.
 */
@RunWith(RobolectricTestRunner::class)
class YahoraDatabaseMigrationTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun deleteAnyLeftoverDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `migrating from 1 to 2 adds entries and keeps existing tags`() = runTest {
        createVersion1Database()

        val database = Room.databaseBuilder(context, YahoraDatabase::class.java, TEST_DB)
            .addMigrations(YahoraDatabase.MIGRATION_1_2)
            .build()

        try {
            // Touching the database triggers the migration and schema validation.
            val tags = database.tagDao().observeAll().first()
            assertEquals(listOf("Coffee"), tags.map { it.name })

            // The new table exists and the old tag can now receive entries.
            val tagId = tags.single().id
            assertEquals(0, database.entryDao().countForTag(tagId))
            database.entryDao().insert(Entry(tagId = tagId, timestamp = 1_700_000_000_000))
            assertEquals(1, database.entryDao().countForTag(tagId))
        } finally {
            database.close()
        }
    }

    /** Recreates exactly what version 1 of the schema looked like. */
    private fun createVersion1Database() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `tags` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`is_favorite` INTEGER NOT NULL, " +
                            "`name_key` TEXT NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name_key` " +
                            "ON `tags` (`name_key`)",
                    )
                    // Room stores the schema fingerprint here and refuses to open a
                    // database whose recorded hash does not match the migrated schema.
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS room_master_table " +
                            "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
                    )
                    db.execSQL(
                        "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                            "VALUES(42, '$VERSION_1_IDENTITY_HASH')",
                    )
                    db.execSQL(
                        "INSERT INTO tags (name, is_favorite, name_key) " +
                            "VALUES ('Coffee', 0, 'coffee')",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** Copied from app/schemas/…/1.json. */
        const val VERSION_1_IDENTITY_HASH = "ea4ef6690f187d26529803493202cb27"
    }
}
