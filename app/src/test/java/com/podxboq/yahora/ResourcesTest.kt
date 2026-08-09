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
package com.podxboq.yahora

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Smoke test for the JVM test infrastructure: proves Robolectric boots and that
 * merged Android resources are visible to unit tests, which the DAO tests rely on.
 */
@RunWith(RobolectricTestRunner::class)
class ResourcesTest {

    @Test
    fun `app name resolves from the default locale`() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("Yahora", context.getString(R.string.app_name))
    }
}
