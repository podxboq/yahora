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
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EntryDaoTest {

    private lateinit var database: YahoraDatabase
    private lateinit var tagDao: TagDao
    private lateinit var entryDao: EntryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        ).build()
        tagDao = database.tagDao()
        entryDao = database.entryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an entry is stored against its tag`() = runTest {
        val tagId = tagDao.insert(Tag(name = "Coffee"))

        entryDao.insert(Entry(tagId = tagId, timestamp = 1_700_000_000_000))

        val entries = entryDao.observeForTag(tagId).first()
        assertEquals(1, entries.size)
        assertEquals(1_700_000_000_000, entries.single().timestamp)
    }

    @Test
    fun `an entry cannot reference a tag that does not exist`() = runTest {
        assertThrowsConstraintViolation {
            entryDao.insert(Entry(tagId = 404, timestamp = 1_700_000_000_000))
        }
    }

    @Test
    fun `a tag with entries cannot be deleted`() = runTest {
        val tagId = tagDao.insert(Tag(name = "Coffee"))
        entryDao.insert(Entry(tagId = tagId, timestamp = 1_700_000_000_000))

        assertThrowsConstraintViolation {
            tagDao.deleteById(tagId)
        }

        assertEquals(1, tagDao.observeAll().first().size)
    }

    @Test
    fun `a tag without entries can be deleted`() = runTest {
        val tagId = tagDao.insert(Tag(name = "Coffee"))

        tagDao.deleteById(tagId)

        assertEquals(0, tagDao.observeAll().first().size)
    }

    @Test
    fun `entries are counted per tag`() = runTest {
        val coffee = tagDao.insert(Tag(name = "Coffee"))
        val tea = tagDao.insert(Tag(name = "Tea"))
        entryDao.insert(Entry(tagId = coffee, timestamp = 1))
        entryDao.insert(Entry(tagId = coffee, timestamp = 2))

        assertEquals(2, entryDao.countForTag(coffee))
        assertEquals(0, entryDao.countForTag(tea))
    }

    @Test
    fun `entries for a tag are observed newest first`() = runTest {
        val tagId = tagDao.insert(Tag(name = "Coffee"))
        entryDao.insert(Entry(tagId = tagId, timestamp = 100))
        entryDao.insert(Entry(tagId = tagId, timestamp = 300))
        entryDao.insert(Entry(tagId = tagId, timestamp = 200))

        val entries = entryDao.observeForTag(tagId).first()

        assertEquals(listOf(300L, 200L, 100L), entries.map { it.timestamp })
    }

    private inline fun assertThrowsConstraintViolation(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected the foreign key constraint to reject the statement")
        } catch (expected: SQLiteConstraintException) {
            // Foreign keys are enforced by the database, not just by the repository.
        }
    }
}
