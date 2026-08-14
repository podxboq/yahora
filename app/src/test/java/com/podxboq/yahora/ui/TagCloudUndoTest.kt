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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.Tag
import com.podxboq.yahora.data.TagRepository
import com.podxboq.yahora.data.YahoraDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The snackbar and its undo action, wired to a real ViewModel: the offer is the
 * only chance to take a tap back without going to the tag's history.
 */
@RunWith(RobolectricTestRunner::class)
class TagCloudUndoTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: YahoraDatabase
    private lateinit var viewModel: TagCloudViewModel
    private var tagId = 0L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        )
            // Synchronous executors: the compose clock cannot wait on Room's
            // own background threads, so the tap's work has to land inline.
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .allowMainThreadQueries()
            .build()
        viewModel = TagCloudViewModel(
            tagRepository = TagRepository(database.tagDao()),
            entryRepository = EntryRepository(database.entryDao()),
        )
        tagId = runBlocking { database.tagDao().insert(Tag(name = "Coffee")) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `tapping a tag offers to undo it`() {
        composeRule.setContent { TagCloudScreen(viewModel, onOpenTagDetail = {}) }

        composeRule.onNodeWithText("Coffee").performClick()
        composeRule.onNodeWithText("Undo").performClick()

        assertEquals(0, runBlocking { database.entryDao().countForTag(tagId) })
    }

    @Test
    fun `letting the snackbar go keeps the entry`() {
        composeRule.setContent { TagCloudScreen(viewModel, onOpenTagDetail = {}) }

        composeRule.onNodeWithText("Coffee").performClick()
        // Long past the snackbar's own lifetime.
        composeRule.mainClock.advanceTimeBy(30_000)

        assertEquals(1, runBlocking { database.entryDao().countForTag(tagId) })
    }
}
