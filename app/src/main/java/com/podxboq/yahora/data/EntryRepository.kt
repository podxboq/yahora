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

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.Flow

/** Outcome of logging one occurrence of a tag. */
sealed interface LogEntryResult {
    /** [entryId] is what makes the tap undoable without looking it up again. */
    data class Logged(val entryId: Long, val timestamp: Long) : LogEntryResult

    /** The tag was deleted between the cloud being drawn and the tap landing. */
    data object TagNotFound : LogEntryResult
}

/**
 * @param now the clock, injectable so tests can pin the timestamp instead of
 * asserting against wall time.
 */
class EntryRepository(
    private val entryDao: EntryDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeEntriesForTag(tagId: Long): Flow<List<Entry>> = entryDao.observeForTag(tagId)

    /** The tags that hold at least one entry, and so cannot be deleted. */
    fun observeTagIdsWithEntries(): Flow<List<Long>> = entryDao.observeTagIdsWithEntries()

    suspend fun logEntry(tagId: Long): LogEntryResult {
        val timestamp = now()
        return try {
            val id = entryDao.insert(Entry(tagId = tagId, timestamp = timestamp))
            LogEntryResult.Logged(entryId = id, timestamp = timestamp)
        } catch (_: SQLiteConstraintException) {
            // The foreign key rejected it: that tag no longer exists.
            LogEntryResult.TagNotFound
        }
    }

    /** Removes a single entry — how a mistaken tap is taken back. */
    suspend fun deleteEntry(id: Long) = entryDao.deleteById(id)
}
