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

@Database(
    entities = [Tag::class],
    version = 1,
    exportSchema = true,
)
abstract class YahoraDatabase : RoomDatabase() {

    abstract fun tagDao(): TagDao

    companion object {
        private const val DATABASE_NAME = "yahora.db"

        @Volatile
        private var instance: YahoraDatabase? = null

        fun getInstance(context: Context): YahoraDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): YahoraDatabase =
            Room.databaseBuilder(context, YahoraDatabase::class.java, DATABASE_NAME)
                // Foreign keys are enforced per connection and are off by default;
                // Entry will depend on this once it exists.
                .build()
    }
}
