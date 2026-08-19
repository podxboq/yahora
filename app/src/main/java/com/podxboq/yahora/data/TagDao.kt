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
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    /**
     * Ordered with Android's LOCALIZED collation, an ICU-backed collation the
     * framework adds on top of stock SQLite. Plain byte comparison would push
     * "Árbol" and "ñu" past "Zumo"; the normalized key would do the same, since
     * it only lowercases. LOCALIZED groups case together, so ordering still
     * ignores case, and the favorite flag never influences position.
     *
     * It follows the device locale, which is resolved when the connection is
     * opened — after a system language change the order settles on next launch.
     */
    @Query("SELECT * FROM tags ORDER BY name COLLATE LOCALIZED ASC")
    fun observeAll(): Flow<List<Tag>>

    /** Null once the tag is gone, so a screen showing it can react. */
    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Tag?>

    @Query("SELECT * FROM tags WHERE name_key = :nameKey LIMIT 1")
    suspend fun findByNameKey(nameKey: String): Tag?

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Tag?

    /**
     * Rewrites a tag in place. Prefer [renameTo] over calling this with a
     * hand-built [Tag]: `copy` would carry the old `name_key` along, and a key
     * that lags behind its name keeps the old name reserved for ever.
     *
     * @throws android.database.sqlite.SQLiteConstraintException on a duplicate.
     */
    @Update
    suspend fun update(tag: Tag)

    /**
     * Inserts a tag, aborting if another tag already uses the same name.
     * @throws android.database.sqlite.SQLiteConstraintException on a duplicate.
     */
    @Insert
    suspend fun insert(tag: Tag): Long

    /**
     * Flips the favorite flag in the database rather than writing a value read
     * earlier, so two rapid taps cannot settle on a stale one. Nothing happens
     * if the tag is gone.
     *
     * Favoriting never touches `name`, so ordering cannot shift as a side
     * effect: the star is emphasis, not precedence.
     */
    @Query("UPDATE tags SET is_favorite = NOT is_favorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    /**
     * Deletes a tag. Fails if the tag still has entries — history is never
     * discarded as a side effect.
     *
     * @throws android.database.sqlite.SQLiteConstraintException if entries exist.
     */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** @see purgeById — never call this without deleting the tag in the same breath. */
    @Query("DELETE FROM entries WHERE tag_id = :id")
    suspend fun deleteEntriesOf(id: Long)

    /**
     * Deletes a tag together with its history. This is the one operation that
     * discards entries, and it exists for debug builds only — see
     * [TagRepository.purgeTag], which is what decides whether it may run.
     *
     * The entries go first and the tag second, inside a transaction: the foreign
     * key is still RESTRICT, so the order is not a preference but the only one
     * that works, and the transaction is what stops a failure halfway from
     * leaving a tag whose history was already thrown away.
     */
    @Transaction
    suspend fun purgeById(id: Long) {
        deleteEntriesOf(id)
        deleteById(id)
    }
}

/** Looks a tag up by a raw, user-typed name. */
suspend fun TagDao.findByName(rawName: String): Tag? =
    findByNameKey(TagName.normalize(rawName))

/**
 * Renames [tag], rebuilding it so the derived `name_key` is computed afresh
 * from the new name instead of being copied from the old one.
 */
suspend fun TagDao.renameTo(tag: Tag, name: String) =
    update(Tag(id = tag.id, name = name, isFavorite = tag.isFavorite))
