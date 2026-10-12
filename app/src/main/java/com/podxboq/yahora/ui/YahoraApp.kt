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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.podxboq.yahora.data.BackupRepository
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.TagRepository

/** Where the app is. The cloud is home; the other two sit on top of it. */
private sealed interface Destination {
    data object Cloud : Destination
    data class TagDetail(val tagId: Long) : Destination
    data object Statistics : Destination
}

/** Carries the destination across process death as plain text. */
private val DestinationSaver = Saver<Destination, String>(
    save = { destination ->
        when (destination) {
            Destination.Cloud -> "cloud"
            Destination.Statistics -> "statistics"
            is Destination.TagDetail -> "tag:${destination.tagId}"
        }
    },
    restore = { saved ->
        when {
            saved == "statistics" -> Destination.Statistics
            saved.startsWith("tag:") -> Destination.TagDetail(saved.removePrefix("tag:").toLong())
            else -> Destination.Cloud
        }
    },
)

/**
 * The whole app: the tag cloud, with one tag's history or the statistics on
 * top of it.
 *
 * Three destinations, each one step from home, do not justify a navigation
 * library — the destination is the entire back stack, and [rememberSaveable]
 * carries it across process death.
 */
@Composable
fun YahoraApp(
    tagRepository: TagRepository,
    entryRepository: EntryRepository,
    backupRepository: BackupRepository,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable(stateSaver = DestinationSaver) {
        mutableStateOf<Destination>(Destination.Cloud)
    }
    val goHome = { destination = Destination.Cloud }

    BackHandler(enabled = destination != Destination.Cloud, onBack = goHome)

    AnimatedContent(
        targetState = destination,
        // Screens slide in over the cloud and back out again, so the
        // hierarchy stays legible instead of the screen simply swapping.
        transitionSpec = {
            val direction = if (targetState != Destination.Cloud) 1 else -1
            slideInHorizontally { width -> direction * width } + fadeIn() togetherWith fadeOut()
        },
        label = "destination",
        modifier = modifier,
    ) { target ->
        when (target) {
            Destination.Cloud -> TagCloudScreen(
                viewModel = viewModel(
                    factory = TagCloudViewModel.factory(
                        tagRepository,
                        entryRepository,
                        backupRepository,
                    ),
                ),
                onOpenTagDetail = { tag -> destination = Destination.TagDetail(tag.id) },
                onOpenStatistics = { destination = Destination.Statistics },
            )

            is Destination.TagDetail -> TagDetailScreen(
                // Keyed by tag: each tag's expansion state is its own.
                viewModel = viewModel(
                    key = "tag-detail-${target.tagId}",
                    factory = TagDetailViewModel.factory(
                        target.tagId,
                        tagRepository,
                        entryRepository,
                    ),
                ),
                onBack = goHome,
            )

            Destination.Statistics -> StatisticsScreen(
                viewModel = viewModel(
                    factory = StatisticsViewModel.factory(tagRepository, entryRepository),
                ),
                onBack = goHome,
            )
        }
    }
}
