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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.TagRepository

/**
 * The whole app: the tag cloud, and one tag's history on top of it.
 *
 * Two destinations do not justify a navigation library — the tag being looked
 * at is the entire back stack, and [rememberSaveable] carries it across process
 * death.
 */
@Composable
fun YahoraApp(
    tagRepository: TagRepository,
    entryRepository: EntryRepository,
    modifier: Modifier = Modifier,
) {
    var detailTagId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = detailTagId != null) { detailTagId = null }

    AnimatedContent(
        targetState = detailTagId,
        // The detail slides in over the cloud and back out again, so the
        // hierarchy stays legible instead of the screen simply swapping.
        transitionSpec = {
            val direction = if (targetState != null) 1 else -1
            slideInHorizontally { width -> direction * width } + fadeIn() togetherWith fadeOut()
        },
        label = "destination",
        modifier = modifier,
    ) { tagId ->
        if (tagId == null) {
            TagCloudScreen(
                viewModel = viewModel(
                    factory = TagCloudViewModel.factory(tagRepository, entryRepository),
                ),
                onOpenTagDetail = { tag -> detailTagId = tag.id },
            )
        } else {
            TagDetailScreen(
                // Keyed by tag: each tag's expansion state is its own.
                viewModel = viewModel(
                    key = "tag-detail-$tagId",
                    factory = TagDetailViewModel.factory(tagId, tagRepository, entryRepository),
                ),
                onBack = { detailTagId = null },
            )
        }
    }
}
