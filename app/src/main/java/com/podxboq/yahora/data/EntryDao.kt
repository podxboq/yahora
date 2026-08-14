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
interface EntryDao {

    @Query("SELECT * FROM entries WHERE tag_id = :tagId ORDER BY timestamp DESC")
    fun observeForTag(tagId: Long): Flow<List<Entry>>

    @Query("SELECT COUNT(*) FROM entries WHERE tag_id = :tagId")
    suspend fun countForTag(tagId: Long): Int

    /**
     * The tags that hold at least one entry — that is, the ones that cannot be
     * deleted. Observed rather than counted on demand so the cloud's menu
     * reflects a tap the moment it lands.
     */
    @Query("SELECT DISTINCT tag_id FROM entries")
    fun observeTagIdsWithEntries(): Flow<List<Long>>

    /**
     * @throws android.database.sqlite.SQLiteConstraintException if [Entry.tagId]
     * does not match an existing tag.
     */
    @Insert
    suspend fun insert(entry: Entry): Long

    /** Deletes one entry. Nothing happens if it is already gone. */
    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
