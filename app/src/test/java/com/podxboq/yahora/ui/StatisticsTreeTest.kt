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
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsTreeTest {

    private val apple = Tag(id = 1, name = "Apple")
    private val coffee = Tag(id = 2, name = "Coffee")
    private val tea = Tag(id = 3, name = "Tea")

    /** In the cloud's order, which is by name. */
    private val tags = listOf(apple, coffee, tea)

    @Test
    fun `entries are counted by year, month and tag, newest first`() {
        val years = buildStatistics(
            tags,
            listOf(
                entry(coffee, "2026-08-14T09:00"),
                entry(coffee, "2026-08-20T09:00"),
                entry(tea, "2026-08-21T17:00"),
                entry(coffee, "2026-07-01T09:00"),
                entry(tea, "2025-12-31T23:00"),
            ),
            ZoneOffset.UTC,
        )

        assertEquals(listOf(2026, 2025), years.map { it.year })
        val august = years.first().months.first()
        assertEquals(YearMonth.of(2026, 8), august.month)
        assertEquals(listOf("Coffee" to 2, "Tea" to 1), august.tags.map { it.tag.name to it.count })
        assertEquals(listOf("2026-08", "2026-07"), years.first().months.map { it.key })
    }

    @Test
    fun `tags within a month keep the cloud's order, not the count's`() {
        val month = buildStatistics(
            tags,
            listOf(
                entry(tea, "2026-08-01T09:00"),
                entry(tea, "2026-08-02T09:00"),
                entry(tea, "2026-08-03T09:00"),
                entry(apple, "2026-08-04T09:00"),
                entry(coffee, "2026-08-05T09:00"),
                entry(coffee, "2026-08-06T09:00"),
            ),
            ZoneOffset.UTC,
        ).single().months.single()

        assertEquals(listOf("Apple", "Coffee", "Tea"), month.tags.map { it.tag.name })
    }

    @Test
    fun `totals and tag counts add up each level`() {
        val year = buildStatistics(
            tags,
            listOf(
                entry(coffee, "2026-08-14T09:00"),
                entry(tea, "2026-08-14T10:00"),
                entry(coffee, "2026-07-14T09:00"),
            ),
            ZoneOffset.UTC,
        ).single()

        assertEquals(3, year.total)
        assertEquals(2, year.months.first().total)
        assertEquals(2, year.tagCount)
    }

    @Test
    fun `an entry counts in the month of the user's own calendar`() {
        // 23:30 UTC on 31 July is already August in Madrid.
        val years = buildStatistics(
            tags,
            listOf(entry(coffee, "2026-07-31T23:30")),
            ZoneId.of("Europe/Madrid"),
        )

        assertEquals(YearMonth.of(2026, 8), years.single().months.single().month)
    }

    @Test
    fun `nothing logged means no years`() {
        assertTrue(buildStatistics(tags, emptyList(), ZoneOffset.UTC).isEmpty())
    }

    private fun entry(tag: Tag, utc: String) = Entry(
        tagId = tag.id,
        timestamp = LocalDateTime.parse(utc).toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
}
