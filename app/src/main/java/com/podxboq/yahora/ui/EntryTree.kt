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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * A node of the entry tree, carrying a [key] that identifies it across the whole
 * tree — that key is what expansion state is stored against.
 */
sealed interface EntryNode {
    val key: String
}

/** A branch: it holds children, and says how many while it is collapsed. */
sealed interface BranchNode : EntryNode {
    val childCount: Int
}

/**
 * One logged entry, at the wall-clock time it happened. The leaf level: it holds
 * nothing, so it states its own time down to the second instead of a count.
 */
data class TimeNode(val time: LocalDateTime, val entry: Entry) : EntryNode {
    // The entry id, not the time: two taps can land within the same second.
    override val key: String = "entry-${entry.id}"
}

data class DayNode(val date: LocalDate, val times: List<TimeNode>) : BranchNode {
    override val key: String = date.toString()
    override val childCount: Int get() = times.size
}

data class MonthNode(val month: YearMonth, val days: List<DayNode>) : BranchNode {
    override val key: String = month.toString()
    override val childCount: Int get() = days.size
}

/**
 * Groups entries by month and day of [zone] — the calendar the user reads their
 * day in, which is not the UTC instant the timestamp is stored as. Within a day
 * the entries are listed one by one, each at its own time.
 *
 * Every level is ordered newest first, matching how the entries themselves are
 * read from the database.
 */
fun List<Entry>.groupIntoMonths(zone: ZoneId): List<MonthNode> =
    map { entry -> entry to LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timestamp), zone) }
        .groupBy { (_, local) -> YearMonth.from(local) }
        .toSortedMap(reverseOrder())
        .map { (month, monthEntries) ->
            MonthNode(month = month, days = monthEntries.toDays())
        }

private fun List<Pair<Entry, LocalDateTime>>.toDays(): List<DayNode> =
    groupBy { (_, local) -> local.toLocalDate() }
        .toSortedMap(reverseOrder())
        .map { (date, dayEntries) ->
            DayNode(
                date = date,
                times = dayEntries
                    .sortedByDescending { (entry, _) -> entry.timestamp }
                    .map { (entry, local) -> TimeNode(time = local, entry = entry) },
            )
        }
