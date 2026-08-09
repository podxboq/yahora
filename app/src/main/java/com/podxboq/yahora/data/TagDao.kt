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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    /**
     * Ordered by the normalized key so that ordering ignores case, and so that
     * the favorite flag never influences position.
     */
    @Query("SELECT * FROM tags ORDER BY name_key ASC")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name_key = :nameKey LIMIT 1")
    suspend fun findByNameKey(nameKey: String): Tag?

    /**
     * Inserts a tag, aborting if another tag already uses the same name.
     * @throws android.database.sqlite.SQLiteConstraintException on a duplicate.
     */
    @Insert
    suspend fun insert(tag: Tag): Long
}

/** Looks a tag up by a raw, user-typed name. */
suspend fun TagDao.findByName(rawName: String): Tag? =
    findByNameKey(TagName.normalize(rawName))
