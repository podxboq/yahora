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
import com.podxboq.yahora.data.TagName
import com.podxboq.yahora.data.TagRepository
import com.podxboq.yahora.data.YahoraDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    fun `the draft never grows past the maximum length`() = runTest(dispatcher) {
        viewModel.onAddTagClick()

        viewModel.onDraftNameChange("a".repeat(TagName.MAX_LENGTH + 50))

        // Pasting an oversized name keeps what fits instead of dropping it all.
        assertEquals(TagName.MAX_LENGTH, viewModel.uiState.value.draftName.length)
    }

    @Test
    fun `truncating the draft does not split an emoji in half`() = runTest(dispatcher) {
        viewModel.onAddTagClick()

        viewModel.onDraftNameChange("👍".repeat(TagName.MAX_LENGTH + 10))

        val draft = viewModel.uiState.value.draftName
        assertEquals(TagName.MAX_LENGTH, draft.codePointCount(0, draft.length))
        assertTrue(draft.endsWith("👍"))
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
    fun `long pressing a tag opens its menu and dismissing closes it`() = runTest(dispatcher) {
        val tag = Tag(id = 7, name = "Coffee")

        viewModel.onTagLongClick(tag)
        assertEquals(7L, viewModel.uiState.value.menuTagId)

        viewModel.onDismissMenu()
        assertNull(viewModel.uiState.value.menuTagId)
    }

    @Test
    fun `a long press does not log an entry`() = runTest(dispatcher) {
        viewModel.onAddTagClick()
        viewModel.onDraftNameChange("Coffee")
        viewModel.onConfirmAddTag()
        advanceUntilIdle()
        val tag = viewModel.uiState.value.tags.single()

        viewModel.onTagLongClick(tag)
        advanceUntilIdle()

        assertEquals(0, database.entryDao().countForTag(tag.id))
    }

    @Test
    fun `only one menu is open at a time`() = runTest(dispatcher) {
        viewModel.onTagLongClick(Tag(id = 1, name = "Coffee"))
        viewModel.onTagLongClick(Tag(id = 2, name = "Tea"))

        assertEquals(2L, viewModel.uiState.value.menuTagId)
    }

    @Test
    fun `renaming starts from the current name and closes the menu`() = runTest(dispatcher) {
        val tag = addTag("Coffee")

        viewModel.onTagLongClick(tag)
        viewModel.onRenameClick(tag)

        val state = viewModel.uiState.value
        assertEquals(tag.id, state.renamingTagId)
        assertEquals("Coffee", state.draftName)
        assertNull(state.menuTagId)
        assertNull(state.nameError)
    }

    @Test
    fun `confirming a rename replaces the name and closes the dialog`() = runTest(dispatcher) {
        val tag = addTag("Coffee")

        viewModel.onRenameClick(tag)
        viewModel.onDraftNameChange("Morning coffee")
        viewModel.onConfirmRename()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.renamingTagId)
        assertNull(state.nameError)
        assertEquals(listOf("Morning coffee"), state.tags.map { it.name })
    }

    @Test
    fun `renaming onto another tag's name keeps the dialog open`() = runTest(dispatcher) {
        val coffee = addTag("Coffee")
        addTag("Tea")

        viewModel.onRenameClick(coffee)
        viewModel.onDraftNameChange("tea")
        viewModel.onConfirmRename()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(coffee.id, state.renamingTagId)
        assertEquals(TagNameError.DUPLICATE, state.nameError)
        assertEquals(listOf("Coffee", "Tea"), state.tags.map { it.name })
    }

    @Test
    fun `renaming to a blank name reports the error`() = runTest(dispatcher) {
        val tag = addTag("Coffee")

        viewModel.onRenameClick(tag)
        viewModel.onDraftNameChange("   ")
        viewModel.onConfirmRename()
        advanceUntilIdle()

        assertEquals(TagNameError.BLANK, viewModel.uiState.value.nameError)
        assertEquals(listOf("Coffee"), viewModel.uiState.value.tags.map { it.name })
    }

    @Test
    fun `dismissing the rename dialog leaves the name alone`() = runTest(dispatcher) {
        val tag = addTag("Coffee")

        viewModel.onRenameClick(tag)
        viewModel.onDraftNameChange("Tea")
        viewModel.onDismissRenameDialog()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.renamingTagId)
        assertEquals("", state.draftName)
        assertEquals(listOf("Coffee"), state.tags.map { it.name })
    }

    @Test
    fun `renaming a tag that no longer exists announces the failure`() = runTest(dispatcher) {
        val ghost = Tag(id = 404, name = "Ghost")

        viewModel.onRenameClick(ghost)
        viewModel.onDraftNameChange("Coffee")
        viewModel.onConfirmRename()
        advanceUntilIdle()

        assertEquals(TagCloudMessage.RenameFailed(tagName = "Ghost"), viewModel.messages.first())
        assertNull(viewModel.uiState.value.renamingTagId)
    }

    @Test
    fun `a renamed tag takes its new place in the cloud`() = runTest(dispatcher) {
        addTag("Coffee")
        val water = addTag("Water")

        viewModel.onRenameClick(water)
        viewModel.onDraftNameChange("Ache")
        viewModel.onConfirmRename()
        advanceUntilIdle()

        // Sorting is alphabetical and nothing pins a tag to where it was.
        assertEquals(listOf("Ache", "Coffee"), viewModel.uiState.value.tags.map { it.name })
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

    /** Creates a tag through the ViewModel and hands back the stored one. */
    private fun TestScope.addTag(name: String): Tag {
        viewModel.onAddTagClick()
        viewModel.onDraftNameChange(name)
        viewModel.onConfirmAddTag()
        advanceUntilIdle()
        return viewModel.uiState.value.tags.single { it.name == name }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
