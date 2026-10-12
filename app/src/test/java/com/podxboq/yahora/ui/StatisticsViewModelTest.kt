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
class StatisticsViewModelTest {

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
    fun `the current year and month start open`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(setOf("year-2026", "2026-10"), viewModel.uiState.value.expandedKeys)
    }

    @Test
    fun `without a filter every tag counts and no totals are shown`() = runTest(dispatcher) {
        val coffee = database.tagDao().insert(Tag(name = "Coffee"))
        val tea = database.tagDao().insert(Tag(name = "Tea"))
        log(coffee, "2026-10-01T09:00")
        log(tea, "2026-10-01T17:00")
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showsTotals)
        assertEquals(listOf("Coffee", "Tea"), state.years.single().months.single().tags.map { it.tag.name })
    }

    @Test
    fun `picking tags narrows the counts to them and shows totals`() = runTest(dispatcher) {
        val coffee = database.tagDao().insert(Tag(name = "Coffee"))
        val tea = database.tagDao().insert(Tag(name = "Tea"))
        log(coffee, "2026-10-01T09:00")
        log(coffee, "2026-10-02T09:00")
        log(tea, "2026-09-01T17:00")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onToggleTag(coffee)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showsTotals)
        assertEquals(2, state.years.single().total)
        // The month with only tea is gone while coffee alone is picked.
        assertEquals(listOf("2026-10"), state.years.single().months.map { it.key })

        viewModel.onToggleTag(coffee)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showsTotals)
        assertEquals(2, viewModel.uiState.value.years.single().months.size)
    }

    @Test
    fun `a picked tag that is deleted stops counting`() = runTest(dispatcher) {
        val coffee = database.tagDao().insert(Tag(name = "Coffee"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onToggleTag(coffee)
        advanceUntilIdle()
        database.tagDao().deleteById(coffee)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.selectedTagIds.isEmpty())
    }

    @Test
    fun `new entries are counted as they land`() = runTest(dispatcher) {
        val coffee = database.tagDao().insert(Tag(name = "Coffee"))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasEntries)

        log(coffee, "2026-10-01T09:00")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasEntries)
        assertEquals(1, viewModel.uiState.value.years.single().total)
    }

    private fun viewModel() = StatisticsViewModel(
        tagRepository = TagRepository(database.tagDao()),
        entryRepository = EntryRepository(database.entryDao()),
        zone = ZoneOffset.UTC,
        now = { at("2026-10-12T10:00") },
    )

    private suspend fun log(tagId: Long, utc: String) {
        database.entryDao().insert(Entry(tagId = tagId, timestamp = at(utc)))
    }

    private fun at(utc: String): Long = LocalDateTime.parse(utc).toInstant(ZoneOffset.UTC).toEpochMilli()
}
