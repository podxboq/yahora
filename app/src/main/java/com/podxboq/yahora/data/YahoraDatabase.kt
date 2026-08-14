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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Tag::class, Entry::class],
    version = 2,
    exportSchema = true,
)
abstract class YahoraDatabase : RoomDatabase() {

    abstract fun tagDao(): TagDao

    abstract fun entryDao(): EntryDao

    companion object {
        private const val DATABASE_NAME = "yahora.db"

        /**
         * Adds the entries table. The statements are copied verbatim from the
         * exported schema in `app/schemas`, which is what Room validates the
         * migrated database against.
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tag_id` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) " +
                        "ON UPDATE RESTRICT ON DELETE RESTRICT )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_entries_tag_id` ON `entries` (`tag_id`)",
                )
            }
        }

        @Volatile
        private var instance: YahoraDatabase? = null

        fun getInstance(context: Context): YahoraDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): YahoraDatabase =
            Room.databaseBuilder(context, YahoraDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
