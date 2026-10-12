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

import com.podxboq.yahora.data.Entry
import com.podxboq.yahora.data.Tag
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** How many times [tag] was logged in one month. */
data class TagCount(val tag: Tag, val count: Int)

data class MonthStatistics(val month: YearMonth, val tags: List<TagCount>) {
    val key: String = month.toString()

    /** Only meaningful over tags the user grouped on purpose; see [StatisticsUiState]. */
    val total: Int get() = tags.sumOf { it.count }
}

data class YearStatistics(val year: Int, val months: List<MonthStatistics>) {
    val key: String = "year-$year"

    val total: Int get() = months.sumOf { it.total }

    /** Different tags logged at any point in the year. */
    val tagCount: Int get() = months.flatMap { it.tags }.map { it.tag.id }.toSet().size
}

/**
 * Counts entries by year, month and tag, in the calendar of [zone].
 *
 * Years and months run newest first, like the history. Within a month the tags
 * keep the order of [tags] — the cloud's, by name — so a tag is found where the
 * eye already looks for it, and the list does not reshuffle as counts change.
 * Months, and tags within them, with nothing logged are left out.
 */
fun buildStatistics(tags: List<Tag>, entries: List<Entry>, zone: ZoneId): List<YearStatistics> {
    val countsByMonth: Map<YearMonth, Map<Long, Int>> = entries
        .groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }
        .mapValues { (_, monthEntries) -> monthEntries.groupingBy { it.tagId }.eachCount() }

    return countsByMonth.keys
        .sortedDescending()
        .map { month ->
            val counts = countsByMonth.getValue(month)
            MonthStatistics(
                month = month,
                tags = tags.mapNotNull { tag -> counts[tag.id]?.let { TagCount(tag, it) } },
            )
        }
        .filter { it.tags.isNotEmpty() }
        .groupBy { it.month.year }
        .map { (year, months) -> YearStatistics(year, months) }
}
