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
package com.podxboq.yahora.ui

import androidx.room.Room
import com.podxboq.yahora.data.Entry
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.Tag
import com.podxboq.yahora.data.TagRepository
import com.podxboq.yahora.data.YahoraDatabase
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TagDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: YahoraDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `the screen is titled after the tag`() = runTest(dispatcher) {
        val tagId = database.tagDao().insert(Tag(name = "Coffee"))

        val viewModel = viewModelFor(tagId)
        advanceUntilIdle()

        assertEquals("Coffee", viewModel.uiState.value.tagName)
    }

    @Test
    fun `entries are exposed as a month and day tree`() = runTest(dispatcher) {
        val tagId = database.tagDao().insert(Tag(name = "Coffee"))
        logEntries(tagId, "2026-08-14T09:15:30", "2026-08-14T09:47:02", "2026-07-02T18:00:00")

        val viewModel = viewModelFor(tagId)
        advanceUntilIdle()

        val months = viewModel.uiState.value.months
        assertEquals(listOf("2026-08", "2026-07"), months.map { it.key })
        assertEquals(2, months.first().days.single().childCount)
    }

    @Test
    fun `a tag with no entries has an empty tree`() = runTest(dispatcher) {
        val tagId = database.tagDao().insert(Tag(name = "Coffee"))

        val viewModel = viewModelFor(tagId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.months.isEmpty())
    }

    @Test
    fun `the tree starts fully collapsed`() = runTest(dispatcher) {
        val tagId = database.tagDao().insert(Tag(name = "Coffee"))
        logEntries(tagId, "2026-08-14T09:15")

        val viewModel = viewModelFor(tagId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.expandedKeys.isEmpty())
    }

    @Test
    fun `toggling a node expands it and toggling again collapses it`() = runTest(dispatcher) {
        val tagId = database.tagDao().insert(Tag(name = "Coffee"))
        logEntries(tagId, "2026-08-14T09:15")
        val viewModel = viewModelFor(tagId)
        advanceUntilIdle()

        viewModel.onToggleNode("2026-08")
        assertTrue(viewModel.uiState.value.isExpanded("2026-08"))

        viewModel.onToggleNode("2026-08")
        assertFalse(viewModel.uiState.value.isExpanded("2026-08"))
    }

    @Test
    fun `expanding a day keeps its month expanded`() = runTest(dispatcher) {
        val tagId = database.tagDao().insert(Tag(name = "Coffee"))
        logEntries(tagId, "2026-08-14T09:15")
        val viewModel = viewModelFor(tagId)
        advanceUntilIdle()

        viewModel.onToggleNode("2026-08")
        viewModel.onToggleNode("2026-08-14")

        val state = viewModel.uiState.value
        assertTrue(state.isExpanded("2026-08"))
        assertTrue(state.isExpanded("2026-08-14"))
    }

    @Test
    fun `a new entry shows up in the tree`() = runTest(dispatcher) {
        val tagId = database.tagDao().insert(Tag(name = "Coffee"))
        val viewModel = viewModelFor(tagId)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.months.isEmpty())

        logEntries(tagId, "2026-08-14T09:15")
        advanceUntilIdle()

        assertEquals("2026-08", viewModel.uiState.value.months.single().key)
    }

    private suspend fun logEntries(tagId: Long, vararg localDateTimes: String) {
        localDateTimes.forEach { localDateTime ->
            database.entryDao().insert(
                Entry(
                    tagId = tagId,
                    timestamp = LocalDateTime.parse(localDateTime)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli(),
                ),
            )
        }
    }

    private fun viewModelFor(tagId: Long) = TagDetailViewModel(
        tagId = tagId,
        tagRepository = TagRepository(database.tagDao()),
        entryRepository = EntryRepository(database.entryDao()),
        // Pinned so grouping never depends on where the test machine sits.
        zone = ZoneOffset.UTC,
    )
}
