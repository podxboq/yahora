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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.podxboq.yahora.data.Tag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagCloudContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping add tag asks for a new tag`() {
        var addClicked = false
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(),
                callbacks = noopCallbacks.copy(onAddTagClick = { addClicked = true }),
            )
        }

        composeRule.onNodeWithText("Add tag").performClick()

        assert(addClicked) { "Expected the add-tag action to be invoked" }
    }

    @Test
    fun `existing tags are listed`() {
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(
                    tags = listOf(Tag(id = 1, name = "Coffee"), Tag(id = 2, name = "Tea")),
                ),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("Coffee").assertIsDisplayed()
        composeRule.onNodeWithText("Tea").assertIsDisplayed()
    }

    @Test
    fun `tapping a tag reports it, with no dialog in between`() {
        var tapped: Tag? = null
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(coffee)),
                callbacks = noopCallbacks.copy(onTagClick = { tapped = it }),
            )
        }

        composeRule.onNodeWithText("Coffee").performClick()

        assert(tapped == coffee) { "Expected the tapped tag to be reported, was $tapped" }
        // A short tap must not open anything: no confirmation, no dialog.
        composeRule.onNodeWithText("Create").assertDoesNotExist()
    }

    @Test
    fun `the dialog reports a duplicate name`() {
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(
                    isAddDialogVisible = true,
                    draftName = "Coffee",
                    nameError = TagNameError.DUPLICATE,
                ),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("A tag with that name already exists").assertIsDisplayed()
    }

    @Test
    fun `the dialog reports a blank name`() {
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(isAddDialogVisible = true, nameError = TagNameError.BLANK),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("Enter a name").assertIsDisplayed()
    }

    @Test
    fun `typing a name and confirming reports it`() {
        var typed: String? = null
        var confirmed = false
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(isAddDialogVisible = true),
                callbacks = noopCallbacks.copy(
                    onDraftNameChange = { typed = it },
                    onConfirmAddTag = { confirmed = true },
                ),
            )
        }

        composeRule.onNodeWithText("Name").performTextInput("Coffee")
        composeRule.onNodeWithText("Create").performClick()

        assert(typed == "Coffee") { "Expected the typed name to be reported, was $typed" }
        assert(confirmed) { "Expected the confirm action to be invoked" }
    }

    private val noopCallbacks = TagCloudCallbacks(
        onAddTagClick = {},
        onDraftNameChange = {},
        onConfirmAddTag = {},
        onDismissAddDialog = {},
        onTagClick = {},
    )
}
