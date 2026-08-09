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

/** Outcome of trying to create a tag. */
sealed interface AddTagResult {
    data class Created(val id: Long) : AddTagResult
    data object DuplicateName : AddTagResult
    data object BlankName : AddTagResult
}

class TagRepository(private val tagDao: TagDao) {

    fun observeTags(): Flow<List<Tag>> = tagDao.observeAll()

    /**
     * Creates a tag from a raw, user-typed name. The name is stored trimmed but
     * with the typed capitalization intact; uniqueness ignores case and
     * surrounding whitespace.
     */
    suspend fun addTag(rawName: String): AddTagResult {
        val name = rawName.trim()
        if (name.isEmpty()) return AddTagResult.BlankName
        if (tagDao.findByName(name) != null) return AddTagResult.DuplicateName

        return try {
            AddTagResult.Created(tagDao.insert(Tag(name = name)))
        } catch (_: SQLiteConstraintException) {
            // The unique index is the real guard: it also covers the race where
            // the same name is inserted between the check above and this insert.
            AddTagResult.DuplicateName
        }
    }
}
