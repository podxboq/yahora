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
class EntryRepositoryTest {

    private lateinit var database: YahoraDatabase
    private lateinit var repository: EntryRepository
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        ).build()
        repository = EntryRepository(database.entryDao()) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `logging an entry stamps the current time`() = runTest {
        val tagId = database.tagDao().insert(Tag(name = "Coffee"))

        val result = repository.logEntry(tagId)

        assertEquals(LogEntryResult.Logged(now), result)
        assertEquals(listOf(now), repository.observeEntriesForTag(tagId).first().map { it.timestamp })
    }

    @Test
    fun `each tap logs a separate entry`() = runTest {
        val tagId = database.tagDao().insert(Tag(name = "Coffee"))

        repository.logEntry(tagId)
        now += 60_000
        repository.logEntry(tagId)

        val timestamps = repository.observeEntriesForTag(tagId).first().map { it.timestamp }
        assertEquals(listOf(1_700_000_060_000L, 1_700_000_000_000L), timestamps)
    }

    @Test
    fun `logging against a tag that no longer exists reports failure`() = runTest {
        val result = repository.logEntry(tagId = 404)

        assertEquals(LogEntryResult.TagNotFound, result)
    }
}
