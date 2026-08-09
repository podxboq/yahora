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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TagRepositoryTest {

    private lateinit var database: YahoraDatabase
    private lateinit var repository: TagRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        ).build()
        repository = TagRepository(database.tagDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `adding a new tag reports success`() = runTest {
        val result = repository.addTag("Coffee")

        assertTrue(result is AddTagResult.Created)
        assertEquals(listOf("Coffee"), repository.observeTags().first().map { it.name })
    }

    @Test
    fun `the stored name keeps the typed capitalization but is trimmed`() = runTest {
        repository.addTag("  Morning Coffee  ")

        assertEquals("Morning Coffee", repository.observeTags().first().single().name)
    }

    @Test
    fun `a name that already exists in another case is rejected`() = runTest {
        repository.addTag("Coffee")

        val result = repository.addTag("coffee")

        assertEquals(AddTagResult.DuplicateName, result)
        assertEquals(1, repository.observeTags().first().size)
    }

    @Test
    fun `an accented name that already exists is rejected`() = runTest {
        repository.addTag("Café")

        val result = repository.addTag("CAFÉ")

        assertEquals(AddTagResult.DuplicateName, result)
        assertEquals(1, repository.observeTags().first().size)
    }

    @Test
    fun `a blank name is rejected`() = runTest {
        assertEquals(AddTagResult.BlankName, repository.addTag(""))
        assertEquals(AddTagResult.BlankName, repository.addTag("   "))
        assertTrue(repository.observeTags().first().isEmpty())
    }

    @Test
    fun `a name of exactly the maximum length is accepted`() = runTest {
        val name = "a".repeat(TagName.MAX_LENGTH)

        assertTrue(repository.addTag(name) is AddTagResult.Created)
        assertEquals(name, repository.observeTags().first().single().name)
    }

    @Test
    fun `a name longer than the maximum is rejected`() = runTest {
        val result = repository.addTag("a".repeat(TagName.MAX_LENGTH + 1))

        assertEquals(AddTagResult.NameTooLong, result)
        assertTrue(repository.observeTags().first().isEmpty())
    }

    @Test
    fun `length is measured in code points, not UTF-16 units`() = runTest {
        // Each of these emoji is a surrogate pair: String.length would report
        // twice the number the user actually typed.
        val name = "👍".repeat(TagName.MAX_LENGTH)

        assertTrue(repository.addTag(name) is AddTagResult.Created)
    }

    @Test
    fun `surrounding whitespace does not count towards the limit`() = runTest {
        val name = "  " + "a".repeat(TagName.MAX_LENGTH) + "  "

        assertTrue(repository.addTag(name) is AddTagResult.Created)
    }

    @Test
    fun `distinct names are all kept`() = runTest {
        repository.addTag("Coffee")
        repository.addTag("Tea")

        assertEquals(listOf("Coffee", "Tea"), repository.observeTags().first().map { it.name })
    }
}
