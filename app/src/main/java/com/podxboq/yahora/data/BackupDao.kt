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
import androidx.room.Transaction

/** The whole log at once, which is what a backup and a restore deal in. */
@Dao
interface BackupDao {

    @Query("SELECT * FROM tags ORDER BY id")
    suspend fun allTags(): List<Tag>

    @Query("SELECT * FROM entries ORDER BY timestamp, id")
    suspend fun allEntries(): List<Entry>

    /**
     * Tags and entries read together, so a tap landing between the two reads
     * cannot leave an entry in the file whose tag is not.
     */
    @Transaction
    suspend fun snapshot(): Pair<List<Tag>, List<Entry>> = allTags() to allEntries()

    /** @see replaceAll — never call this on its own. */
    @Query("DELETE FROM entries")
    suspend fun deleteAllEntries()

    /** @see replaceAll — never call this on its own. */
    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Insert
    suspend fun insertTags(tags: List<Tag>)

    @Insert
    suspend fun insertEntries(entries: List<Entry>)

    /**
     * Throws the current log away and puts [tags] and [entries] in its place.
     *
     * Entries go before tags, since the foreign key is RESTRICT, and all of it
     * happens in one transaction: if any insert fails, the log the user had is
     * still there, untouched.
     */
    @Transaction
    suspend fun replaceAll(tags: List<Tag>, entries: List<Entry>) {
        deleteAllEntries()
        deleteAllTags()
        insertTags(tags)
        insertEntries(entries)
    }
}
