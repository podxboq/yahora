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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `renaming a tag replaces its name`() = runTest {
        val id = createdId(repository.addTag("Coffee"))

        val result = repository.renameTag(id, "  Morning Coffee  ")

        assertEquals(RenameTagResult.Renamed, result)
        // Trimmed, like a newly created name, and the id never changes.
        assertEquals(listOf("Morning Coffee"), repository.observeTags().first().map { it.name })
        assertEquals(id, repository.observeTags().first().single().id)
    }

    @Test
    fun `renaming also moves the uniqueness key`() = runTest {
        val id = createdId(repository.addTag("Coffee"))

        repository.renameTag(id, "Tea")

        // The key is derived from the name; if it lagged behind, the old name
        // would stay reserved and the new one would still be free.
        assertNull(database.tagDao().findByName("Coffee"))
        assertEquals(id, database.tagDao().findByName("TEA")?.id)
    }

    @Test
    fun `renaming to a name another tag already uses is rejected`() = runTest {
        val id = createdId(repository.addTag("Coffee"))
        repository.addTag("Tea")

        val result = repository.renameTag(id, "  tea ")

        assertEquals(RenameTagResult.DuplicateName, result)
        assertEquals(listOf("Coffee", "Tea"), repository.observeTags().first().map { it.name })
    }

    @Test
    fun `a tag can be recapitalized without colliding with itself`() = runTest {
        val id = createdId(repository.addTag("coffee"))

        val result = repository.renameTag(id, "Coffee")

        assertEquals(RenameTagResult.Renamed, result)
        assertEquals("Coffee", repository.observeTags().first().single().name)
    }

    @Test
    fun `renaming to a blank name is rejected`() = runTest {
        val id = createdId(repository.addTag("Coffee"))

        assertEquals(RenameTagResult.BlankName, repository.renameTag(id, "   "))
        assertEquals("Coffee", repository.observeTags().first().single().name)
    }

    @Test
    fun `renaming to a name longer than the maximum is rejected`() = runTest {
        val id = createdId(repository.addTag("Coffee"))

        val result = repository.renameTag(id, "a".repeat(TagName.MAX_LENGTH + 1))

        assertEquals(RenameTagResult.NameTooLong, result)
        assertEquals("Coffee", repository.observeTags().first().single().name)
    }

    @Test
    fun `renaming a tag that no longer exists reports it`() = runTest {
        assertEquals(RenameTagResult.TagNotFound, repository.renameTag(404, "Coffee"))
    }

    @Test
    fun `renaming keeps the favorite flag and the entries`() = runTest {
        // Inserted through the DAO: the repository has no favorite flag yet.
        val id = database.tagDao().insert(Tag(name = "Coffee", isFavorite = true))
        database.entryDao().insert(Entry(tagId = id, timestamp = 1_700_000_000_000))

        repository.renameTag(id, "Tea")

        val tag = repository.observeTags().first().single { it.id == id }
        assertEquals("Tea", tag.name)
        assertTrue(tag.isFavorite)
        assertEquals(1, database.entryDao().countForTag(id))
    }

    @Test
    fun `deleting a tag with no entries removes it`() = runTest {
        val id = createdId(repository.addTag("Coffee"))
        repository.addTag("Tea")

        val result = repository.deleteTag(id)

        assertEquals(DeleteTagResult.Deleted, result)
        assertEquals(listOf("Tea"), repository.observeTags().first().map { it.name })
    }

    @Test
    fun `deleting a tag that has entries is refused`() = runTest {
        val id = createdId(repository.addTag("Coffee"))
        database.entryDao().insert(Entry(tagId = id, timestamp = 1_700_000_000_000))

        val result = repository.deleteTag(id)

        // History is never discarded as a side effect of deleting a tag.
        assertEquals(DeleteTagResult.HasEntries, result)
        assertEquals(listOf("Coffee"), repository.observeTags().first().map { it.name })
        assertEquals(1, database.entryDao().countForTag(id))
    }

    @Test
    fun `deleting a tag that is already gone is not an error`() = runTest {
        assertEquals(DeleteTagResult.Deleted, repository.deleteTag(404))
    }

    @Test
    fun `a tag can be made a favorite and back`() = runTest {
        val id = createdId(repository.addTag("Coffee"))
        assertFalse(repository.observeTags().first().single().isFavorite)

        repository.toggleFavorite(id)
        assertTrue(repository.observeTags().first().single().isFavorite)

        repository.toggleFavorite(id)
        assertFalse(repository.observeTags().first().single().isFavorite)
    }

    @Test
    fun `favorites do not reorder the cloud`() = runTest {
        repository.addTag("Coffee")
        val water = createdId(repository.addTag("Water"))

        repository.toggleFavorite(water)

        // The star is emphasis, never precedence: sorting stays alphabetical.
        assertEquals(listOf("Coffee", "Water"), repository.observeTags().first().map { it.name })
    }

    @Test
    fun `favoriting a tag that is already gone does nothing`() = runTest {
        repository.toggleFavorite(404)

        assertTrue(repository.observeTags().first().isEmpty())
    }

    private fun createdId(result: AddTagResult): Long =
        (result as AddTagResult.Created).id
}
