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
package com.podxboq.yahora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.TagRepository
import com.podxboq.yahora.data.YahoraDatabase
import com.podxboq.yahora.ui.YahoraApp
import com.podxboq.yahora.ui.theme.YahoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = YahoraDatabase.getInstance(applicationContext)
        // Discarding a tag's history is a debug-only affordance: it exists so a
        // session of trying the app out can be cleared, and the app that ships
        // keeps the rule that history outlives tags.
        val tagRepository = TagRepository(database.tagDao(), canPurgeTags = BuildConfig.DEBUG)
        val entryRepository = EntryRepository(database.entryDao())

        setContent {
            YahoraTheme {
                YahoraApp(tagRepository = tagRepository, entryRepository = entryRepository)
            }
        }
    }
}
