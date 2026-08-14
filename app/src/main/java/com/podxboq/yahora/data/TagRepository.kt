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
    data object NameTooLong : AddTagResult
}

/** Outcome of trying to delete a tag. */
sealed interface DeleteTagResult {
    /** The tag is gone — including when it already was. */
    data object Deleted : DeleteTagResult

    /** It still holds entries, so the database refused: history outlives tags. */
    data object HasEntries : DeleteTagResult
}

/** Outcome of trying to rename a tag. */
sealed interface RenameTagResult {
    data object Renamed : RenameTagResult
    data object DuplicateName : RenameTagResult
    data object BlankName : RenameTagResult
    data object NameTooLong : RenameTagResult

    /** The tag was deleted between the menu opening and the rename landing. */
    data object TagNotFound : RenameTagResult
}

class TagRepository(private val tagDao: TagDao) {

    fun observeTags(): Flow<List<Tag>> = tagDao.observeAll()

    fun observeTag(id: Long): Flow<Tag?> = tagDao.observeById(id)

    /**
     * Creates a tag from a raw, user-typed name. The name is stored trimmed but
     * with the typed capitalization intact; uniqueness ignores case and
     * surrounding whitespace.
     */
    suspend fun addTag(rawName: String): AddTagResult {
        val name = rawName.trim()
        if (name.isEmpty()) return AddTagResult.BlankName
        // The UI already caps typing; this guards every other caller.
        if (TagName.lengthOf(name) > TagName.MAX_LENGTH) return AddTagResult.NameTooLong
        if (tagDao.findByName(name) != null) return AddTagResult.DuplicateName

        return try {
            AddTagResult.Created(tagDao.insert(Tag(name = name)))
        } catch (_: SQLiteConstraintException) {
            // The unique index is the real guard: it also covers the race where
            // the same name is inserted between the check above and this insert.
            AddTagResult.DuplicateName
        }
    }

    /**
     * Marks a tag as a favorite, or stops doing so. The flag only changes visual
     * emphasis: it never reorders the cloud, and there is nothing to report back
     * — the tags flow carries the new value.
     */
    suspend fun toggleFavorite(id: Long) = tagDao.toggleFavorite(id)

    /**
     * Deletes a tag, provided it holds no entries. The foreign key is what
     * enforces that — the UI hides the action, but the guarantee lives in the
     * database, not in whatever the screen happened to know.
     */
    suspend fun deleteTag(id: Long): DeleteTagResult =
        try {
            tagDao.deleteById(id)
            DeleteTagResult.Deleted
        } catch (_: SQLiteConstraintException) {
            DeleteTagResult.HasEntries
        }

    /**
     * Gives an existing tag a new name, under the same rules that govern
     * creating one. The tag keeps its id, its favorite flag and all its
     * entries — only the name and the key derived from it change.
     */
    suspend fun renameTag(id: Long, rawName: String): RenameTagResult {
        val tag = tagDao.findById(id) ?: return RenameTagResult.TagNotFound
        val name = rawName.trim()
        if (name.isEmpty()) return RenameTagResult.BlankName
        if (TagName.lengthOf(name) > TagName.MAX_LENGTH) return RenameTagResult.NameTooLong
        // A tag never collides with itself: recapitalizing "coffee" to "Coffee"
        // leaves the key untouched and must go through.
        val clash = tagDao.findByName(name)
        if (clash != null && clash.id != id) return RenameTagResult.DuplicateName

        return try {
            tagDao.renameTo(tag, name)
            RenameTagResult.Renamed
        } catch (_: SQLiteConstraintException) {
            RenameTagResult.DuplicateName
        }
    }
}
