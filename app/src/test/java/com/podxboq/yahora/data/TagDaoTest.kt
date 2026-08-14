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
class TagDaoTest {

    private lateinit var database: YahoraDatabase
    private lateinit var dao: TagDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        ).build()
        dao = database.tagDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserted tag is read back`() = runTest {
        dao.insert(Tag(name = "Coffee"))

        val tags = dao.observeAll().first()

        assertEquals(listOf("Coffee"), tags.map { it.name })
        assertEquals(false, tags.single().isFavorite)
    }

    @Test
    fun `a single tag is observed by id`() = runTest {
        val id = dao.insert(Tag(name = "Coffee"))
        dao.insert(Tag(name = "Tea"))

        assertEquals("Coffee", dao.observeById(id).first()?.name)
    }

    @Test
    fun `observing a tag that does not exist yields null`() = runTest {
        assertEquals(null, dao.observeById(404).first())
    }

    @Test
    fun `the favorite flag flips back and forth`() = runTest {
        val id = dao.insert(Tag(name = "Coffee"))

        dao.toggleFavorite(id)
        assertEquals(true, dao.findById(id)?.isFavorite)

        dao.toggleFavorite(id)
        assertEquals(false, dao.findById(id)?.isFavorite)
    }

    @Test
    fun `flipping the favorite flag leaves the name and its key alone`() = runTest {
        val id = dao.insert(Tag(name = "Coffee"))
        val before = dao.findById(id)

        dao.toggleFavorite(id)

        val after = dao.findById(id)
        assertEquals(before?.name, after?.name)
        assertEquals(before?.nameKey, after?.nameKey)
    }

    @Test
    fun `flipping one tag's favorite flag leaves the others alone`() = runTest {
        val coffee = dao.insert(Tag(name = "Coffee"))
        val tea = dao.insert(Tag(name = "Tea", isFavorite = true))

        dao.toggleFavorite(coffee)

        assertEquals(true, dao.findById(tea)?.isFavorite)
    }

    @Test
    fun `tag name that differs only in case is rejected`() = runTest {
        dao.insert(Tag(name = "Coffee"))

        assertThrowsConstraintViolation {
            dao.insert(Tag(name = "COFFEE"))
        }
    }

    @Test
    fun `tag name that differs only in surrounding whitespace is rejected`() = runTest {
        dao.insert(Tag(name = "Coffee"))

        assertThrowsConstraintViolation {
            dao.insert(Tag(name = "  Coffee  "))
        }
    }

    @Test
    fun `case folding also covers accented characters`() = runTest {
        dao.insert(Tag(name = "Café"))

        assertThrowsConstraintViolation {
            dao.insert(Tag(name = "CAFÉ"))
        }
    }

    @Test
    fun `tags are observed in alphabetical order regardless of insertion order`() = runTest {
        dao.insert(Tag(name = "Water"))
        dao.insert(Tag(name = "Coffee"))
        dao.insert(Tag(name = "medication"))

        val tags = dao.observeAll().first()

        assertEquals(listOf("Coffee", "medication", "Water"), tags.map { it.name })
    }

    @Test
    fun `accented and non-ascii names sort in their alphabetical position`() = runTest {
        // With SQLite's default byte comparison these would land after "Zumo",
        // because "Á" and "ñ" sort above "z" in UTF-8.
        dao.insert(Tag(name = "Zumo"))
        dao.insert(Tag(name = "Árbol"))
        dao.insert(Tag(name = "ñu"))
        dao.insert(Tag(name = "Agua"))

        val tags = dao.observeAll().first()

        assertEquals(listOf("Agua", "Árbol", "ñu", "Zumo"), tags.map { it.name })
    }

    @Test
    fun `favorites do not alter the alphabetical order`() = runTest {
        dao.insert(Tag(name = "Water", isFavorite = true))
        dao.insert(Tag(name = "Coffee"))

        val tags = dao.observeAll().first()

        assertEquals(listOf("Coffee", "Water"), tags.map { it.name })
    }

    @Test
    fun `finding by name ignores case and surrounding whitespace`() = runTest {
        dao.insert(Tag(name = "Coffee"))

        assertEquals("Coffee", dao.findByName("  cOfFeE ")?.name)
        assertEquals(null, dao.findByName("tea"))
    }

    private inline fun assertThrowsConstraintViolation(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected the unique name constraint to reject the insert")
        } catch (expected: SQLiteConstraintException) {
            // The database, not just the repository, enforces name uniqueness.
        }
    }
}
