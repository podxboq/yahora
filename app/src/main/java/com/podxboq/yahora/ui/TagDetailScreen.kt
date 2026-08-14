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

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podxboq.yahora.R
import com.podxboq.yahora.data.Entry
import com.podxboq.yahora.ui.theme.YahoraTheme
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the detail screen can ask of its host. */
data class TagDetailCallbacks(
    val onToggleNode: (String) -> Unit,
    val onBack: () -> Unit,
    val onEntryLongClick: (TimeNode) -> Unit,
    val onDismissEntryMenu: () -> Unit,
    val onDeleteEntryClick: (TimeNode) -> Unit,
    val onConfirmDeleteEntry: () -> Unit,
    val onDismissDeleteEntry: () -> Unit,
)

@Composable
fun TagDetailScreen(
    viewModel: TagDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TagDetailContent(
        state = state,
        callbacks = TagDetailCallbacks(
            onToggleNode = viewModel::onToggleNode,
            onBack = onBack,
            onEntryLongClick = viewModel::onEntryLongClick,
            onDismissEntryMenu = viewModel::onDismissEntryMenu,
            onDeleteEntryClick = viewModel::onDeleteEntryClick,
            onConfirmDeleteEntry = viewModel::onConfirmDeleteEntry,
            onDismissDeleteEntry = viewModel::onDismissDeleteEntry,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailContent(
    state: TagDetailUiState,
    callbacks: TagDetailCallbacks,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(state.tagName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
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
        if (state.months.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.tag_detail_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            val rows = remember(state.months, state.expandedKeys) {
                state.months.flattenVisible(state.expandedKeys)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(rows, key = { it.node.key }) { row ->
                    TreeRow(
                        row = row,
                        isExpanded = state.isExpanded(row.node.key),
                        isMenuOpen = state.menuEntryId != null &&
                            state.menuEntryId == (row.node as? TimeNode)?.entry?.id,
                        callbacks = callbacks,
                        // Rows shift as branches open and close; animating the
                        // move keeps the eye on the row it was reading.
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    state.entryPendingDeletion?.let { node ->
        DeleteEntryDialog(node = node, callbacks = callbacks)
    }
}

/**
 * Removing an entry is asked about first: unlike deleting an empty tag, this
 * one loses history, and nothing brings it back.
 */
@Composable
private fun DeleteEntryDialog(node: TimeNode, callbacks: TagDetailCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onDismissDeleteEntry,
        title = { Text(stringResource(R.string.delete_entry_dialog_title)) },
        text = { Text(stringResource(R.string.delete_entry_dialog_text, node.label())) },
        confirmButton = {
            TextButton(onClick = callbacks.onConfirmDeleteEntry) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = callbacks.onDismissDeleteEntry) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** One node, plus where it sits in the tree. */
private data class VisibleRow(val node: EntryNode, val depth: Int)

/**
 * Flattens the tree into the rows currently on screen. A node's children are
 * only reachable when every ancestor is open, so a collapsed month hides its
 * days' own expansion state without discarding it.
 */
private fun List<MonthNode>.flattenVisible(expandedKeys: Set<String>): List<VisibleRow> =
    buildList {
        this@flattenVisible.forEach { month ->
            add(VisibleRow(month, depth = 0))
            if (month.key !in expandedKeys) return@forEach
            month.days.forEach { day ->
                add(VisibleRow(day, depth = 1))
                if (day.key !in expandedKeys) return@forEach
                day.times.forEach { time -> add(VisibleRow(time, depth = 2)) }
            }
        }
    }

@Composable
private fun TreeRow(
    row: VisibleRow,
    isExpanded: Boolean,
    isMenuOpen: Boolean,
    callbacks: TagDetailCallbacks,
    modifier: Modifier = Modifier,
) {
    // Entries are the last level: they have nothing left to open, and they are
    // the only rows there is anything to do to.
    val branch = row.node as? BranchNode
    val entry = row.node as? TimeNode
    val view = LocalView.current

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (entry != null) {
                        // The short tap is left free on purpose: editing a
                        // timestamp is what will claim it.
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = {
                                // Same tick the cloud gives: the menu is opening.
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                callbacks.onEntryLongClick(entry)
                            },
                        )
                    } else {
                        Modifier.clickable { callbacks.onToggleNode(row.node.key) }
                    },
                )
                .padding(
                    start = 16.dp + 24.dp * row.depth,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (branch != null) {
                ExpandIndicator(isExpanded = isExpanded)
            } else {
                Spacer(Modifier.width(24.dp))
            }
            Text(
                text = row.node.label(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // An entry states its own time in the label; only branches count.
            if (branch != null) {
                Text(
                    text = branch.childCountLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (entry != null) {
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = callbacks.onDismissEntryMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.entry_menu_delete)) },
                    onClick = { callbacks.onDeleteEntryClick(entry) },
                )
            }
        }
    }
}

/** A chevron that turns as the branch opens: the state change is the point. */
@Composable
private fun ExpandIndicator(isExpanded: Boolean) {
    val collapsedRotation = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 90f else -90f
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else collapsedRotation,
        label = "chevronRotation",
    )

    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = stringResource(
            if (isExpanded) R.string.tag_detail_collapse else R.string.tag_detail_expand,
        ),
        modifier = Modifier
            .size(24.dp)
            .rotate(rotation),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The row's own name. Date patterns come from resources so a new locale is a
 * translation, never a code change.
 */
@Composable
private fun EntryNode.label(): String {
    // Read through LocalConfiguration so a locale change recomposes the labels.
    val locale = LocalConfiguration.current.locales[0]

    return when (this) {
        is MonthNode -> dateFormatter(R.string.month_year_pattern, locale).format(month)
        is DayNode -> dateFormatter(R.string.day_pattern, locale).format(date)
        is TimeNode -> dateFormatter(R.string.entry_time_pattern, locale).format(time)
    }
}

@Composable
private fun dateFormatter(patternRes: Int, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(stringResource(patternRes), locale)

/** How many children the row holds — days or entries, one level down. */
@Composable
private fun BranchNode.childCountLabel(): String {
    val plural = when (this) {
        is MonthNode -> R.plurals.day_count
        is DayNode -> R.plurals.entry_count
    }
    return pluralStringResource(plural, childCount, childCount)
}

@Preview(showBackground = true)
@Composable
private fun TagDetailContentPreview() {
    val entries = listOf(
        Entry(id = 1, tagId = 1, timestamp = 1_755_162_900_000),
        Entry(id = 2, tagId = 1, timestamp = 1_755_164_820_000),
        Entry(id = 3, tagId = 1, timestamp = 1_754_120_000_000),
    )

    YahoraTheme(dynamicColor = false) {
        TagDetailContent(
            state = TagDetailUiState(
                tagName = "Coffee",
                months = entries.groupIntoMonths(ZoneOffset.UTC),
                expandedKeys = setOf("2026-08"),
            ),
            callbacks = TagDetailCallbacks({}, {}, {}, {}, {}, {}, {}),
        )
    }
}
