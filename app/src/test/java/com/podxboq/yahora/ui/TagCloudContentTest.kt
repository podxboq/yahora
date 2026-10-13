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

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.podxboq.yahora.data.Backup
import com.podxboq.yahora.data.Entry
import com.podxboq.yahora.data.Tag
import org.junit.Assert.assertEquals
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
    fun `only favorite tags carry the star`() {
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(
                    tags = listOf(
                        Tag(id = 1, name = "Coffee", isFavorite = true),
                        Tag(id = 2, name = "Tea"),
                    ),
                ),
                callbacks = noopCallbacks,
            )
        }

        // The star is described for screen readers, so favorites are not
        // signalled by shape alone.
        composeRule.onAllNodesWithContentDescription("Favorite").assertCountEquals(1)
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
    fun `the counter appears only as the limit approaches`() {
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(isAddDialogVisible = true, draftName = "a".repeat(120)),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("120/128").assertIsDisplayed()
    }

    @Test
    fun `no counter while the name is comfortably short`() {
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(isAddDialogVisible = true, draftName = "Coffee"),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("6/128").assertDoesNotExist()
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

    @Test
    fun `long pressing a tag opens its menu instead of logging an entry`() {
        var longPressed: Tag? = null
        var tapped: Tag? = null
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(coffee)),
                callbacks = noopCallbacks.copy(
                    onTagClick = { tapped = it },
                    onTagLongClick = { longPressed = it },
                ),
            )
        }

        composeRule.onNodeWithText("Coffee").performTouchInput { longClick() }

        assert(longPressed == coffee) { "Expected the long press to be reported, was $longPressed" }
        assert(tapped == null) { "A long press must not log an entry" }
    }

    @Test
    fun `no context menu is shown until a tag asks for one`() {
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(Tag(id = 1, name = "Coffee"))),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("View entries").assertDoesNotExist()
    }

    @Test
    fun `the context menu opens the tag detail`() {
        var opened: Tag? = null
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(
                    tags = listOf(coffee),
                    menuTagId = coffee.id,
                    tagIdsWithEntries = setOf(coffee.id),
                ),
                callbacks = noopCallbacks.copy(onViewEntries = { opened = it }),
            )
        }

        composeRule.onNodeWithText("View entries").performClick()

        assert(opened == coffee) { "Expected the detail action to report the tag, was $opened" }
    }

    @Test
    fun `the context menu omits viewing the entries of a tag with none`() {
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(coffee), menuTagId = coffee.id),
                callbacks = noopCallbacks,
            )
        }

        // Nothing to show, so the action is left out — as with delete, the
        // inapplicable half of the pair simply is not there.
        composeRule.onNodeWithText("View entries").assertDoesNotExist()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun `only the long pressed tag shows a menu`() {
        val coffee = Tag(id = 1, name = "Coffee")
        val tea = Tag(id = 2, name = "Tea")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(
                    tags = listOf(coffee, tea),
                    menuTagId = tea.id,
                    tagIdsWithEntries = setOf(coffee.id, tea.id),
                ),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onAllNodesWithText("View entries").assertCountEquals(1)
    }

    @Test
    fun `the context menu offers renaming the tag`() {
        var renaming: Tag? = null
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(coffee), menuTagId = coffee.id),
                callbacks = noopCallbacks.copy(onRenameClick = { renaming = it }),
            )
        }

        composeRule.onNodeWithText("Rename").performClick()

        assert(renaming == coffee) { "Expected the rename action to report the tag, was $renaming" }
    }

    @Test
    fun `the rename dialog opens on the current name`() {
        var confirmed = false
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(renamingTag = coffee, draftName = "Coffee"),
                callbacks = noopCallbacks.copy(onConfirmRename = { confirmed = true }),
            )
        }

        composeRule.onNodeWithText("Rename Coffee").assertIsDisplayed()
        composeRule.onNodeWithText("Coffee").assertIsDisplayed()

        composeRule.onNodeWithText("Rename", substring = false).performClick()

        assert(confirmed) { "Expected the rename to be confirmed" }
    }

    @Test
    fun `the rename dialog reports a duplicate name`() {
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(
                    renamingTag = Tag(id = 1, name = "Coffee"),
                    draftName = "Tea",
                    nameError = TagNameError.DUPLICATE,
                ),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("A tag with that name already exists").assertIsDisplayed()
    }

    @Test
    fun `no rename dialog until a tag is being renamed`() {
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(Tag(id = 1, name = "Coffee"))),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("Rename Coffee").assertDoesNotExist()
    }

    @Test
    fun `the context menu offers deleting a tag with no entries`() {
        var deleted: Tag? = null
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(coffee), menuTagId = coffee.id),
                callbacks = noopCallbacks.copy(onDeleteClick = { deleted = it }),
            )
        }

        composeRule.onNodeWithText("Delete").performClick()

        assert(deleted == coffee) { "Expected the delete action to report the tag, was $deleted" }
        // Nothing is lost, so nothing is confirmed.
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun `the context menu omits deleting a tag that has entries`() {
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(
                    tags = listOf(coffee),
                    menuTagId = coffee.id,
                    tagIdsWithEntries = setOf(coffee.id),
                ),
                callbacks = noopCallbacks,
            )
        }

        // Omitted, never greyed out.
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
        composeRule.onNodeWithText("View entries").assertIsDisplayed()
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
    }

    @Test
    fun `the context menu offers starring a plain tag`() {
        var starred: Tag? = null
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(coffee), menuTagId = coffee.id),
                callbacks = noopCallbacks.copy(onToggleFavorite = { starred = it }),
            )
        }

        composeRule.onNodeWithText("Mark as favorite").performClick()

        assert(starred == coffee) { "Expected the favorite action to report the tag, was $starred" }
    }

    @Test
    fun `the context menu offers unstarring a favorite`() {
        val coffee = Tag(id = 1, name = "Coffee", isFavorite = true)
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(coffee), menuTagId = coffee.id),
                callbacks = noopCallbacks,
            )
        }

        // The action names what it will do, not what the tag currently is.
        composeRule.onNodeWithText("Remove from favorites").assertIsDisplayed()
        composeRule.onNodeWithText("Mark as favorite").assertDoesNotExist()
    }

    @Test
    fun `a tag with entries is never offered for deletion`() {
        val coffee = Tag(id = 1, name = "Coffee")
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(
                    tags = listOf(coffee),
                    menuTagId = coffee.id,
                    tagIdsWithEntries = setOf(coffee.id),
                ),
                callbacks = noopCallbacks,
            )
        }

        // History outlives tags: the menu holds no way to delete this one.
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
        composeRule.onNodeWithText("View entries").assertIsDisplayed()
    }

    @Test
    fun `the overflow menu offers to back up and to restore`() {
        var saved = false
        var restored = false
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(),
                callbacks = noopCallbacks.copy(
                    onSaveBackupClick = { saved = true },
                    onRestoreClick = { restored = true },
                ),
            )
        }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Back up").performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Restore backup").performClick()

        assert(saved && restored) { "Expected both backup actions to be invoked" }
    }

    @Test
    fun `the restore dialog says what the backup holds before replacing anything`() {
        var confirmed = false
        val backup = Backup(
            tags = listOf(Tag(id = 1, name = "Coffee"), Tag(id = 2, name = "Tea")),
            entries = listOf(Entry(tagId = 1, timestamp = 1), Entry(tagId = 2, timestamp = 2)),
            exportedAt = 1_700_000_000_000,
        )
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(pendingRestore = backup),
                callbacks = noopCallbacks.copy(onConfirmRestore = { confirmed = true }),
            )
        }

        composeRule.onNodeWithText("Replace your log?").assertIsDisplayed()
        composeRule.onNodeWithText("2 tags and 2 entries", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Restore").performClick()

        assert(confirmed) { "Expected the restore to be confirmed" }
    }

    @Test
    fun `an uncoloured tag offers a colour, a coloured one offers to remove it`() {
        val plain = Tag(id = 1, name = "Coffee")
        val coloured = Tag(id = 2, name = "Tea", color = 0xFF43A047.toInt())
        var menuFor by mutableStateOf<Long?>(plain.id)
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(tags = listOf(plain, coloured), menuTagId = menuFor),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("Color").assertIsDisplayed()
        composeRule.onNodeWithText("Remove color").assertDoesNotExist()

        menuFor = coloured.id
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Remove color").assertIsDisplayed()
        composeRule.onNodeWithText("Color").assertDoesNotExist()
    }

    @Test
    fun `a hex code typed in the picker is the colour applied`() {
        var applied: Int? = null
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(
                    tags = listOf(Tag(id = 1, name = "Coffee")),
                    colouringTag = Tag(id = 1, name = "Coffee"),
                ),
                callbacks = noopCallbacks.copy(onConfirmColor = { applied = it }),
            )
        }

        composeRule.onNodeWithText("Color for Coffee").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("2e7d32")
        composeRule.onNodeWithText("Apply").performClick()

        assertEquals(0xFF2E7D32.toInt(), applied)
    }

    @Test
    fun `a swatch picked in the picker is the colour applied`() {
        var applied: Int? = null
        composeRule.setContent {
            TagCloudContent(
                state = TagCloudUiState(colouringTag = Tag(id = 1, name = "Coffee")),
                callbacks = noopCallbacks.copy(onConfirmColor = { applied = it }),
            )
        }

        composeRule.onNodeWithContentDescription("Color #1E88E5").performClick()
        composeRule.onNodeWithText("Apply").performClick()

        assertEquals(0xFF1E88E5.toInt(), applied)
    }

    private val noopCallbacks = TagCloudCallbacks(
        onAddTagClick = {},
        onDraftNameChange = {},
        onConfirmAddTag = {},
        onDismissAddDialog = {},
        onTagClick = {},
        onTagLongClick = {},
        onDismissMenu = {},
        onViewEntries = {},
        onRenameClick = {},
        onConfirmRename = {},
        onDismissRenameDialog = {},
        onDeleteClick = {},
        onToggleFavorite = {},
        onColorClick = {},
        onConfirmColor = {},
        onDismissColorDialog = {},
        onRemoveColor = {},
        onStatisticsClick = {},
        onSaveBackupClick = {},
        onRestoreClick = {},
        onConfirmRestore = {},
        onDismissRestoreDialog = {},
    )
}
