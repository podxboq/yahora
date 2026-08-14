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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.TagRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TagDetailUiState(
    val tagName: String = "",
    val months: List<MonthNode> = emptyList(),
    /** Keys of the nodes the user has opened; see [EntryNode.key]. */
    val expandedKeys: Set<String> = emptySet(),
    /** The entry whose context menu is open, if any. */
    val menuEntryId: Long? = null,
    /** The entry waiting to be confirmed for deletion, if any. */
    val entryPendingDeletion: TimeNode? = null,
) {
    fun isExpanded(key: String): Boolean = key in expandedKeys
}

/**
 * The history of one tag, as a month → day → hour tree.
 *
 * @param zone the calendar the entries are grouped by. Injected so tests pin it
 * instead of inheriting whichever zone the machine happens to sit in.
 */
class TagDetailViewModel(
    tagId: Long,
    tagRepository: TagRepository,
    private val entryRepository: EntryRepository,
    zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagDetailUiState())
    val uiState: StateFlow<TagDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                tagRepository.observeTag(tagId),
                entryRepository.observeEntriesForTag(tagId),
            ) { tag, entries ->
                // The tag is gone only if it was deleted while this screen was
                // open; keeping the last name avoids an empty title flashing by.
                tag?.name to entries.groupIntoMonths(zone)
            }.collect { (name, months) ->
                _uiState.update { state ->
                    state.copy(tagName = name ?: state.tagName, months = months)
                }
            }
        }
    }

    /**
     * Opens a collapsed node, or closes an open one. Descendants keep whatever
     * state they had, so reopening a month shows it exactly as it was left.
     */
    fun onToggleNode(key: String) {
        _uiState.update { state ->
            val expanded = if (state.isExpanded(key)) {
                state.expandedKeys - key
            } else {
                state.expandedKeys + key
            }
            state.copy(expandedKeys = expanded)
        }
    }

    /** A long press on an entry offers what can be done to it. */
    fun onEntryLongClick(node: TimeNode) {
        _uiState.update { it.copy(menuEntryId = node.entry.id) }
    }

    fun onDismissEntryMenu() {
        _uiState.update { it.copy(menuEntryId = null) }
    }

    /**
     * Deleting an entry does ask first — unlike deleting an empty tag, this one
     * loses history, and nothing brings it back.
     */
    fun onDeleteEntryClick(node: TimeNode) {
        _uiState.update { it.copy(menuEntryId = null, entryPendingDeletion = node) }
    }

    fun onDismissDeleteEntry() {
        _uiState.update { it.copy(entryPendingDeletion = null) }
    }

    fun onConfirmDeleteEntry() {
        val node = _uiState.value.entryPendingDeletion ?: return
        viewModelScope.launch {
            entryRepository.deleteEntry(node.entry.id)
            _uiState.update { it.copy(entryPendingDeletion = null) }
        }
    }

    companion object {
        fun factory(
            tagId: Long,
            tagRepository: TagRepository,
            entryRepository: EntryRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TagDetailViewModel(tagId, tagRepository, entryRepository) as T
            }
    }
}
