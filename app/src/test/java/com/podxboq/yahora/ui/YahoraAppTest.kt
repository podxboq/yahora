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

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import com.podxboq.yahora.data.Entry
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.Tag
import com.podxboq.yahora.data.TagRepository
import com.podxboq.yahora.data.YahoraDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The route between the two screens: long press, view entries, back. */
@RunWith(RobolectricTestRunner::class)
class YahoraAppTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: YahoraDatabase
    private lateinit var tagRepository: TagRepository
    private lateinit var entryRepository: EntryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        ).build()
        tagRepository = TagRepository(database.tagDao())
        entryRepository = EntryRepository(database.entryDao())
        runBlocking {
            val tagId = database.tagDao().insert(Tag(name = "Coffee"))
            database.entryDao().insert(Entry(tagId = tagId, timestamp = 1_755_162_900_000))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the context menu opens the detail, and back returns to the cloud`() {
        composeRule.setContent { YahoraApp(tagRepository, entryRepository) }

        composeRule.onNodeWithText("Coffee").performTouchInput { longClick() }
        composeRule.onNodeWithText("View entries").performClick()

        // On the detail: a month row, which the cloud never shows.
        composeRule.onNodeWithText("1 day").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Add tag").assertIsDisplayed()
        composeRule.onNodeWithText("1 day").assertDoesNotExist()
    }
}
