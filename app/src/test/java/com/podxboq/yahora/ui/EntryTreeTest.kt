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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure grouping logic: no Android, no database, no Robolectric. */
class EntryTreeTest {

    @Test
    fun `no entries make no months`() {
        assertEquals(emptyList<MonthNode>(), emptyList<Entry>().groupIntoMonths(UTC))
    }

    @Test
    fun `entries are grouped into months and days`() {
        val entries = listOf(
            entryAt("2026-08-14T09:15:30"),
            entryAt("2026-08-14T09:47:02"),
            entryAt("2026-08-14T18:00:00"),
            entryAt("2026-08-02T07:30:11"),
            entryAt("2026-07-31T23:59:59"),
        )

        val months = entries.groupIntoMonths(UTC)

        assertEquals(2, months.size)
        val august = months.first()
        assertEquals(2, august.days.size)
        // A day holds its entries directly — there is no hour level between them.
        assertEquals(3, august.days.first().times.size)
    }

    @Test
    fun `each branch counts its own children`() {
        val entries = listOf(
            entryAt("2026-08-14T09:15:30"),
            entryAt("2026-08-14T09:47:02"),
            entryAt("2026-08-14T18:00:00"),
            entryAt("2026-08-02T07:30:11"),
        )

        val august = entries.groupIntoMonths(UTC).single()

        // A month counts days; a day counts entries.
        assertEquals(2, august.childCount)
        assertEquals(3, august.days.first().childCount)
        assertEquals(1, august.days.last().childCount)
    }

    @Test
    fun `a leaf keeps the wall-clock time down to the second`() {
        val entries = listOf(entryAt("2026-08-14T09:15:30"))

        val time = entries.groupIntoMonths(UTC).single().days.single().times.single()

        assertEquals(LocalDateTime.parse("2026-08-14T09:15:30"), time.time)
    }

    @Test
    fun `every level is ordered newest first`() {
        val entries = listOf(
            entryAt("2026-07-01T10:00:00"),
            entryAt("2026-08-02T08:00:00"),
            entryAt("2026-08-14T09:00:00"),
            entryAt("2026-08-14T18:00:00"),
        )

        val months = entries.groupIntoMonths(UTC)

        assertEquals(listOf("2026-08", "2026-07"), months.map { it.key })
        assertEquals(
            listOf("2026-08-14", "2026-08-02"),
            months.first().days.map { it.key },
        )
        assertEquals(
            listOf(
                LocalDateTime.parse("2026-08-14T18:00:00"),
                LocalDateTime.parse("2026-08-14T09:00:00"),
            ),
            months.first().days.first().times.map { it.time },
        )
    }

    @Test
    fun `entries within the same second keep distinct keys`() {
        // Two taps can land in the same second, so the key is the entry id.
        val entries = listOf(
            entryAt("2026-08-14T09:15:30"),
            entryAt("2026-08-14T09:15:30"),
        )

        val times = entries.groupIntoMonths(UTC).single().days.single().times

        assertEquals(2, times.map { it.key }.toSet().size)
    }

    @Test
    fun `grouping follows the given time zone, not UTC`() {
        // 23:30 UTC is already the next day in Madrid, so it belongs to another
        // month entirely.
        val entry = entryAt("2026-07-31T23:30:00")

        val utcMonth = listOf(entry).groupIntoMonths(UTC).single()
        val madridMonth = listOf(entry).groupIntoMonths(ZoneId.of("Europe/Madrid")).single()

        assertEquals("2026-07", utcMonth.key)
        assertEquals("2026-07-31", utcMonth.days.single().key)
        assertEquals("2026-08", madridMonth.key)
        assertEquals("2026-08-01", madridMonth.days.single().key)
        // The leaf shows the local hour too, not the stored UTC one.
        assertEquals(
            LocalDateTime.parse("2026-08-01T01:30:00"),
            madridMonth.days.single().times.single().time,
        )
    }

    @Test
    fun `node keys identify a node without colliding with another level`() {
        val month = listOf(entryAt("2026-08-14T09:15:30")).groupIntoMonths(UTC).single()
        val day = month.days.single()
        val time = day.times.single()

        // Keys drive expansion state, so all three must stay distinct.
        assertEquals(3, setOf(month.key, day.key, time.key).size)
    }

    private var nextId = 1L

    private fun entryAt(localDateTime: String): Entry =
        Entry(
            id = nextId++,
            tagId = 1,
            timestamp = LocalDateTime.parse(localDateTime).toInstant(ZoneOffset.UTC).toEpochMilli(),
        )

    private companion object {
        val UTC: ZoneId = ZoneOffset.UTC
    }
}
