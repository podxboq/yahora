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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.podxboq.yahora.data.Entry
import com.podxboq.yahora.data.Tag
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatisticsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val coffee = Tag(id = 1, name = "Coffee")
    private val tea = Tag(id = 2, name = "Tea")
    private val entries = listOf(
        entry(coffee, "2026-10-01T09:00"),
        entry(coffee, "2026-10-02T09:00"),
        entry(tea, "2026-10-02T17:00"),
    )

    @Test
    fun `without a filter, periods say how many tags they hold, not a sum`() {
        composeRule.setContent {
            StatisticsContent(state = stateOf(), callbacks = noopCallbacks)
        }

        composeRule.onNodeWithText("2026").assertIsDisplayed()
        composeRule.onNodeWithText("October").assertIsDisplayed()
        // Year and month both: two tags, and no "3" anywhere.
        composeRule.onAllNodesWithText("2 tags").assertCountEquals(2)
        composeRule.onNodeWithText("3").assertDoesNotExist()
        // Each tag states its own count.
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun `with a filter, periods show the total of the picked tags`() {
        composeRule.setContent {
            StatisticsContent(
                state = stateOf(selected = setOf(coffee.id, tea.id)),
                callbacks = noopCallbacks,
            )
        }

        composeRule.onAllNodesWithText("3").assertCountEquals(2)
        composeRule.onNodeWithText("2 tags").assertDoesNotExist()
    }

    @Test
    fun `tapping a tag in the filter toggles it`() {
        var toggled: Long? = null
        composeRule.setContent {
            StatisticsContent(
                state = stateOf(),
                callbacks = noopCallbacks.copy(onToggleTag = { toggled = it }),
            )
        }

        // The filter chip comes first; the tree's chip for Tea follows it.
        composeRule.onAllNodesWithText("Tea")[0].performClick()

        assertEquals(tea.id, toggled)
    }

    @Test
    fun `nothing logged says so`() {
        composeRule.setContent {
            StatisticsContent(state = StatisticsUiState(tags = listOf(coffee)), callbacks = noopCallbacks)
        }

        composeRule.onNodeWithText("Nothing logged yet").assertIsDisplayed()
    }

    private fun stateOf(selected: Set<Long> = emptySet()): StatisticsUiState {
        val counted = if (selected.isEmpty()) entries else entries.filter { it.tagId in selected }
        return StatisticsUiState(
            tags = listOf(coffee, tea),
            years = buildStatistics(listOf(coffee, tea), counted, ZoneOffset.UTC),
            selectedTagIds = selected,
            expandedKeys = setOf("year-2026", "2026-10"),
            hasEntries = true,
        )
    }

    private fun entry(tag: Tag, utc: String) = Entry(
        tagId = tag.id,
        timestamp = LocalDateTime.parse(utc).toInstant(ZoneOffset.UTC).toEpochMilli(),
    )

    private val noopCallbacks = StatisticsCallbacks(onBack = {}, onToggleTag = {}, onToggleNode = {})
}
