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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.podxboq.yahora.data.Entry
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the tag name titles the screen`() {
        composeRule.setContent {
            TagDetailContent(state = stateOf(), callbacks = noopCallbacks)
        }

        composeRule.onNodeWithText("Coffee").assertIsDisplayed()
    }

    @Test
    fun `months are listed with how many days they hold`() {
        composeRule.setContent {
            TagDetailContent(state = stateOf(), callbacks = noopCallbacks)
        }

        composeRule.onNodeWithText("August 2026").assertIsDisplayed()
        composeRule.onNodeWithText("2 days").assertIsDisplayed()
    }

    @Test
    fun `a collapsed month hides its days`() {
        composeRule.setContent {
            TagDetailContent(state = stateOf(), callbacks = noopCallbacks)
        }

        composeRule.onNodeWithText("Friday 14").assertDoesNotExist()
    }

    @Test
    fun `an expanded month shows its days with how many entries they hold`() {
        composeRule.setContent {
            TagDetailContent(state = stateOf(expanded = setOf("2026-08")), callbacks = noopCallbacks)
        }

        composeRule.onNodeWithText("Friday 14").assertIsDisplayed()
        composeRule.onNodeWithText("3 entries").assertIsDisplayed()
        // The entries themselves stay folded until their day is opened.
        composeRule.onNodeWithText("09:15:30").assertDoesNotExist()
    }

    @Test
    fun `an expanded day lists every entry down to the second`() {
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(expanded = setOf("2026-08", "2026-08-14")),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("18:00:00").assertIsDisplayed()
        composeRule.onNodeWithText("09:47:02").assertIsDisplayed()
        composeRule.onNodeWithText("09:15:30").assertIsDisplayed()
    }

    @Test
    fun `an entry carries no count of its own`() {
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(expanded = setOf("2026-08", "2026-08-14")),
                callbacks = noopCallbacks,
            )
        }

        // The count belongs to the day: opening it must not repeat it per entry.
        composeRule.onAllNodesWithText("3 entries").assertCountEquals(1)
        composeRule.onAllNodesWithText("1 entry").assertCountEquals(1)
    }

    @Test
    fun `tapping a month reports the node to toggle`() {
        var toggled: String? = null
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(),
                callbacks = noopCallbacks.copy(onToggleNode = { toggled = it }),
            )
        }

        composeRule.onNodeWithText("August 2026").performClick()

        assert(toggled == "2026-08") { "Expected the month key to be reported, was $toggled" }
    }

    @Test
    fun `tapping an entry toggles nothing`() {
        var toggled: String? = null
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(expanded = setOf("2026-08", "2026-08-14")),
                callbacks = noopCallbacks.copy(onToggleNode = { toggled = it }),
            )
        }

        composeRule.onNodeWithText("09:15:30").performClick()

        assert(toggled == null) { "An entry is a leaf: it has nothing to open, was $toggled" }
    }

    @Test
    fun `going back is offered`() {
        var back = false
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(),
                callbacks = noopCallbacks.copy(onBack = { back = true }),
            )
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        assert(back) { "Expected the back action to be invoked" }
    }

    @Test
    fun `a tag with no entries says so`() {
        composeRule.setContent {
            TagDetailContent(
                state = TagDetailUiState(tagName = "Coffee"),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("No entries yet").assertIsDisplayed()
    }

    @Test
    fun `long pressing an entry opens its menu`() {
        var pressed: TimeNode? = null
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(expanded = setOf("2026-08", "2026-08-14")),
                callbacks = noopCallbacks.copy(onEntryLongClick = { pressed = it }),
            )
        }

        composeRule.onNodeWithText("09:15:30").performTouchInput { longClick() }

        assert(pressed?.entry?.id == 1L) { "Expected the entry to be reported, was $pressed" }
    }

    @Test
    fun `long pressing a branch opens nothing`() {
        var pressed: TimeNode? = null
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(),
                callbacks = noopCallbacks.copy(onEntryLongClick = { pressed = it }),
            )
        }

        composeRule.onNodeWithText("August 2026").performTouchInput { longClick() }

        assert(pressed == null) { "A month is not an entry: nothing to delete, was $pressed" }
    }

    @Test
    fun `the entry menu offers deleting it`() {
        var deleting: TimeNode? = null
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(expanded = setOf("2026-08", "2026-08-14"), menuEntryId = 1L),
                callbacks = noopCallbacks.copy(onDeleteEntryClick = { deleting = it }),
            )
        }

        composeRule.onNodeWithText("Delete").performClick()

        assert(deleting?.entry?.id == 1L) { "Expected the delete action to report the entry" }
    }

    @Test
    fun `deleting an entry is confirmed first`() {
        var confirmed = false
        val doomed = stateOf(expanded = setOf("2026-08", "2026-08-14"))
            .months.single().days.first().times.last()
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(
                    expanded = setOf("2026-08", "2026-08-14"),
                    pendingDeletion = doomed,
                ),
                callbacks = noopCallbacks.copy(onConfirmDeleteEntry = { confirmed = true }),
            )
        }

        // The dialog names the entry it is about to remove — the row behind it
        // shows that time too, so the whole sentence is what identifies it.
        composeRule.onNodeWithText("Delete this entry?").assertIsDisplayed()
        composeRule.onNodeWithText("It was logged at 09:15:30. This cannot be undone.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Delete").performClick()

        assert(confirmed) { "Expected the deletion to be confirmed" }
    }

    @Test
    fun `no confirmation dialog until one is asked for`() {
        composeRule.setContent {
            TagDetailContent(
                state = stateOf(expanded = setOf("2026-08", "2026-08-14")),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onNodeWithText("Delete this entry?").assertDoesNotExist()
    }

    /** Three entries on 14 August, one on 2 August. */
    private fun stateOf(
        expanded: Set<String> = emptySet(),
        menuEntryId: Long? = null,
        pendingDeletion: TimeNode? = null,
    ) = TagDetailUiState(
        tagName = "Coffee",
        months = listOf(
            entryAt(1, "2026-08-14T09:15:30"),
            entryAt(2, "2026-08-14T09:47:02"),
            entryAt(3, "2026-08-14T18:00:00"),
            entryAt(4, "2026-08-02T07:30:11"),
        ).groupIntoMonths(ZoneOffset.UTC),
        expandedKeys = expanded,
        menuEntryId = menuEntryId,
        entryPendingDeletion = pendingDeletion,
    )

    private fun entryAt(id: Long, localDateTime: String): Entry =
        Entry(
            id = id,
            tagId = 1,
            timestamp = LocalDateTime.parse(localDateTime).toInstant(ZoneOffset.UTC).toEpochMilli(),
        )

    private val noopCallbacks = TagDetailCallbacks(
        onToggleNode = {},
        onBack = {},
        onEntryLongClick = {},
        onDismissEntryMenu = {},
        onDeleteEntryClick = {},
        onConfirmDeleteEntry = {},
        onDismissDeleteEntry = {},
    )
}
