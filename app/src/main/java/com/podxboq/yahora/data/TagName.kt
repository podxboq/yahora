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

    fun normalize(rawName: String): String = rawName.trim().lowercase(Locale.ROOT)
}
