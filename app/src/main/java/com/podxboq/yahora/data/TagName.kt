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
package com.podxboq.yahora.data

import java.util.Locale

/**
 * Two tag names are "the same name" when they only differ in surrounding
 * whitespace or letter case. Comparing normalized keys rather than relying on
 * SQLite's NOCASE collation is deliberate: NOCASE only folds ASCII, so it would
 * treat "Café" and "CAFÉ" as different names.
 */
object TagName {

    /**
     * Longest a tag name may be, counted in code points. Without a cap, pasting
     * a long text produces a chip wider than the screen and an unreadable toast.
     */
    const val MAX_LENGTH = 128

    /** Show the remaining-characters counter once this few are left. */
    const val COUNTER_THRESHOLD = 16

    fun normalize(rawName: String): String = rawName.trim().lowercase(Locale.ROOT)

    /**
     * Length as the user perceives it. `String.length` counts UTF-16 units, so
     * it reports 2 for an emoji and would reject names that look well within
     * the limit.
     */
    fun lengthOf(name: String): Int = name.codePointCount(0, name.length)

    /** Keeps the first [MAX_LENGTH] code points, never splitting a surrogate pair. */
    fun truncate(name: String): String =
        if (lengthOf(name) <= MAX_LENGTH) {
            name
        } else {
            name.substring(0, name.offsetByCodePoints(0, MAX_LENGTH))
        }
}
