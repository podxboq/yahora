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
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.Tag
import com.podxboq.yahora.data.TagRepository
import com.podxboq.yahora.data.YahoraDatabase
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TagCloudViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: YahoraDatabase
    private lateinit var viewModel: TagCloudViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        )
            // Route Room's own threads through the test scheduler as well, so
            // that advanceUntilIdle() also drains database work and the tests
            // stay deterministic instead of racing background executors.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        viewModel = TagCloudViewModel(
            tagRepository = TagRepository(database.tagDao()),
            entryRepository = EntryRepository(database.entryDao()) { NOW },
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `the dialog starts hidden and opens on request`() = runTest(dispatcher) {
        assertFalse(viewModel.uiState.value.isAddDialogVisible)

        viewModel.onAddTagClick()

        assertTrue(viewModel.uiState.value.isAddDialogVisible)
        assertEquals("", viewModel.uiState.value.draftName)
    }

    @Test
    fun `confirming a valid name creates the tag and closes the dialog`() = runTest(dispatcher) {
        viewModel.onAddTagClick()
        viewModel.onDraftNameChange("Coffee")
        viewModel.onConfirmAddTag()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAddDialogVisible)
        assertNull(state.nameError)
        assertEquals(listOf("Coffee"), state.tags.map { it.name })
    }

    @Test
    fun `confirming a duplicate name keeps the dialog open and reports the error`() = runTest(dispatcher) {
        viewModel.onAddTagClick()
        viewModel.onDraftNameChange("Coffee")
        viewModel.onConfirmAddTag()
        advanceUntilIdle()

        viewModel.onAddTagClick()
        viewModel.onDraftNameChange("  coffee ")
        viewModel.onConfirmAddTag()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAddDialogVisible)
        assertEquals(TagNameError.DUPLICATE, state.nameError)
        assertEquals(1, state.tags.size)
    }

    @Test
    fun `confirming a blank name reports the error without creating anything`() = runTest(dispatcher) {
        viewModel.onAddTagClick()
        viewModel.onDraftNameChange("   ")
        viewModel.onConfirmAddTag()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAddDialogVisible)
        assertEquals(TagNameError.BLANK, state.nameError)
        assertTrue(state.tags.isEmpty())
    }

    @Test
    fun `editing the name clears a previous error`() = runTest(dispatcher) {
        viewModel.onAddTagClick()
        viewModel.onDraftNameChange("")
        viewModel.onConfirmAddTag()
        advanceUntilIdle()
        assertEquals(TagNameError.BLANK, viewModel.uiState.value.nameError)

        viewModel.onDraftNameChange("Tea")

        assertNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun `dismissing the dialog discards the draft`() = runTest(dispatcher) {
        viewModel.onAddTagClick()
        viewModel.onDraftNameChange("Tea")
        viewModel.onDismissAddDialog()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAddDialogVisible)
        assertEquals("", state.draftName)
        assertTrue(state.tags.isEmpty())
    }

    @Test
    fun `tapping a tag logs an entry and announces it`() = runTest(dispatcher) {
        viewModel.onAddTagClick()
        viewModel.onDraftNameChange("Coffee")
        viewModel.onConfirmAddTag()
        advanceUntilIdle()
        val tag = viewModel.uiState.value.tags.single()

        viewModel.onTagClick(tag)
        advanceUntilIdle()

        assertEquals(
            TagCloudMessage.EntryLogged(tagName = "Coffee", timestamp = NOW),
            viewModel.messages.first(),
        )
        assertEquals(1, database.entryDao().countForTag(tag.id))
    }

    @Test
    fun `tapping the same tag twice logs two entries`() = runTest(dispatcher) {
        viewModel.onAddTagClick()
        viewModel.onDraftNameChange("Coffee")
        viewModel.onConfirmAddTag()
        advanceUntilIdle()
        val tag = viewModel.uiState.value.tags.single()

        viewModel.onTagClick(tag)
        advanceUntilIdle()
        viewModel.onTagClick(tag)
        advanceUntilIdle()

        assertEquals(2, database.entryDao().countForTag(tag.id))
    }

    @Test
    fun `tapping a tag that no longer exists announces the failure`() = runTest(dispatcher) {
        val ghost = Tag(id = 404, name = "Ghost")

        viewModel.onTagClick(ghost)
        advanceUntilIdle()

        assertEquals(TagCloudMessage.EntryFailed(tagName = "Ghost"), viewModel.messages.first())
    }

    @Test
    fun `tags are exposed alphabetically`() = runTest(dispatcher) {
        listOf("Water", "coffee", "Tea").forEach { name ->
            viewModel.onAddTagClick()
            viewModel.onDraftNameChange(name)
            viewModel.onConfirmAddTag()
            advanceUntilIdle()
        }

        assertEquals(
            listOf("coffee", "Tea", "Water"),
            viewModel.uiState.value.tags.map { it.name },
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
