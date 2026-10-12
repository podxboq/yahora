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
import com.podxboq.yahora.data.Entry
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.Tag
import com.podxboq.yahora.data.TagRepository
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatisticsUiState(
    /** Every tag, in the cloud's order: what the filter offers. */
    val tags: List<Tag> = emptyList(),
    val years: List<YearStatistics> = emptyList(),
    /** The tags the user picked to look at together. Empty means all of them. */
    val selectedTagIds: Set<Long> = emptySet(),
    /** Keys of the years and months that are open. */
    val expandedKeys: Set<String> = emptySet(),
    /** Whether anything at all has been logged, filter aside. */
    val hasEntries: Boolean = false,
) {
    /**
     * Whether a year or a month may show a total. Adding coffees to locked
     * doors gives a number that means nothing, so a total only appears once
     * the user has picked which tags belong together.
     */
    val showsTotals: Boolean get() = selectedTagIds.isNotEmpty()

    fun isExpanded(key: String): Boolean = key in expandedKeys
}

/**
 * How many times each tag was logged, by year and month — numbers to read at a
 * glance, not a dashboard to come back to.
 *
 * The filter is a grouping made for the occasion: it lives as long as the
 * screen does, so it never hardens into categories the app has to carry.
 *
 * @param zone the calendar entries are counted in; tests pin it.
 * @param now decides which year and month start open; tests pin it.
 */
class StatisticsViewModel(
    tagRepository: TagRepository,
    entryRepository: EntryRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState(expandedKeys = currentPeriodKeys(now())))
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private var allTags: List<Tag> = emptyList()
    private var allEntries: List<Entry> = emptyList()

    init {
        viewModelScope.launch {
            combine(tagRepository.observeTags(), entryRepository.observeAllEntries()) { tags, entries ->
                tags to entries
            }.collect { (tags, entries) ->
                allTags = tags
                allEntries = entries
                recount()
            }
        }
    }

    /** Adds a tag to the group being looked at, or takes it out. */
    fun onToggleTag(tagId: Long) {
        _uiState.update { state ->
            val selected = state.selectedTagIds
            state.copy(selectedTagIds = if (tagId in selected) selected - tagId else selected + tagId)
        }
        recount()
    }

    fun onToggleNode(key: String) {
        _uiState.update { state ->
            val expanded = state.expandedKeys
            state.copy(expandedKeys = if (key in expanded) expanded - key else expanded + key)
        }
    }

    private fun recount() {
        _uiState.update { state ->
            // A picked tag that has since been deleted simply stops counting.
            val selected = state.selectedTagIds intersect allTags.map { it.id }.toSet()
            val entries = if (selected.isEmpty()) {
                allEntries
            } else {
                allEntries.filter { it.tagId in selected }
            }
            state.copy(
                tags = allTags,
                years = buildStatistics(allTags, entries, zone),
                selectedTagIds = selected,
                hasEntries = allEntries.isNotEmpty(),
            )
        }
    }

    /** The year and month in progress start open: they are what is usually asked about. */
    private fun currentPeriodKeys(now: Long): Set<String> {
        val month = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        return setOf("year-${month.year}", month.toString())
    }

    companion object {
        fun factory(
            tagRepository: TagRepository,
            entryRepository: EntryRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatisticsViewModel(tagRepository, entryRepository) as T
            }
    }
}
