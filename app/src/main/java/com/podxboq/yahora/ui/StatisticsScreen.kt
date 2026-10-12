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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podxboq.yahora.R
import java.time.format.DateTimeFormatter

/** Everything the statistics screen can ask of its host. */
data class StatisticsCallbacks(
    val onBack: () -> Unit,
    val onToggleTag: (Long) -> Unit,
    val onToggleNode: (String) -> Unit,
)

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    StatisticsContent(
        state = state,
        callbacks = StatisticsCallbacks(
            onBack = onBack,
            onToggleTag = viewModel::onToggleTag,
            onToggleNode = viewModel::onToggleNode,
        ),
        modifier = modifier,
    )
}

/** One row of the tree as it stands on screen. */
private sealed interface StatisticsRow {
    val key: String

    data class YearRow(val year: YearStatistics) : StatisticsRow {
        override val key = year.key
    }

    data class MonthRow(val month: MonthStatistics) : StatisticsRow {
        override val key = month.key
    }

    data class TagRow(val month: MonthStatistics, val count: TagCount) : StatisticsRow {
        override val key = "${month.key}-${count.tag.id}"
    }
}

private fun List<YearStatistics>.flattenVisible(expandedKeys: Set<String>): List<StatisticsRow> =
    buildList {
        this@flattenVisible.forEach { year ->
            add(StatisticsRow.YearRow(year))
            if (year.key !in expandedKeys) return@forEach
            year.months.forEach { month ->
                add(StatisticsRow.MonthRow(month))
                if (month.key !in expandedKeys) return@forEach
                month.tags.forEach { add(StatisticsRow.TagRow(month, it)) }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsContent(
    state: StatisticsUiState,
    callbacks: StatisticsCallbacks,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!state.hasEntries) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.statistics_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            return@Scaffold
        }

        val rows = remember(state.years, state.expandedKeys) {
            state.years.flattenVisible(state.expandedKeys)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // The filter scrolls away with the list: with many tags it would
            // otherwise eat the screen the counts are meant to fill.
            item(key = "filter") { TagFilter(state = state, onToggleTag = callbacks.onToggleTag) }
            items(rows, key = { it.key }) { row ->
                StatisticsTreeRow(
                    row = row,
                    state = state,
                    onToggleNode = callbacks.onToggleNode,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * Picking tags narrows the counts to them, and lets years and months add them
 * up. Nothing picked means every tag, with no totals.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFilter(state: StatisticsUiState, onToggleTag: (Long) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.statistics_filter),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.tags.forEach { tag ->
                FilterChip(
                    selected = tag.id in state.selectedTagIds,
                    onClick = { onToggleTag(tag.id) },
                    label = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun StatisticsTreeRow(
    row: StatisticsRow,
    state: StatisticsUiState,
    onToggleNode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val isBranch = row !is StatisticsRow.TagRow
    val depth = when (row) {
        is StatisticsRow.YearRow -> 0
        is StatisticsRow.MonthRow -> 1
        is StatisticsRow.TagRow -> 2
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isBranch) Modifier.clickable { onToggleNode(row.key) } else Modifier)
            .padding(start = 16.dp + 24.dp * depth, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isBranch) {
            ExpandIndicator(isExpanded = state.isExpanded(row.key))
        } else {
            Spacer(Modifier.width(24.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            when (row) {
                is StatisticsRow.YearRow -> RowLabel(row.year.year.toString())
                is StatisticsRow.MonthRow -> RowLabel(
                    DateTimeFormatter
                        .ofPattern(stringResource(R.string.statistics_month_pattern), locale)
                        .format(row.month.month),
                )
                // The tag as the cloud draws it, colour included, so it is
                // recognised rather than read.
                is StatisticsRow.TagRow -> TagChipFace(
                    name = row.count.tag.name,
                    isFavorite = row.count.tag.isFavorite,
                    color = row.count.tag.color?.let(::Color),
                )
            }
        }

        val trailing = when (row) {
            is StatisticsRow.TagRow -> row.count.count.toString()
            is StatisticsRow.MonthRow -> branchSummary(state, row.month.total, row.month.tags.size)
            is StatisticsRow.YearRow -> branchSummary(state, row.year.total, row.year.tagCount)
        }
        Text(
            text = trailing,
            style = if (row is StatisticsRow.TagRow) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = if (row is StatisticsRow.TagRow) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * A total when the user grouped tags on purpose; otherwise only how many tags
 * the period holds, since adding unrelated counts would mean nothing.
 */
@Composable
private fun branchSummary(state: StatisticsUiState, total: Int, tagCount: Int): String =
    if (state.showsTotals) {
        total.toString()
    } else {
        pluralStringResource(R.plurals.tag_count, tagCount, tagCount)
    }

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
