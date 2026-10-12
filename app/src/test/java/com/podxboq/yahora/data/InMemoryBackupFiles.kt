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

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Documents kept in memory, standing in for the ones the system picker grants.
 * A document that was never written cannot be opened, as with a revoked grant.
 */
class InMemoryBackupFiles : BackupFiles {

    private val documents = mutableMapOf<Uri, ByteArray>()

    operator fun get(uri: Uri): String? = documents[uri]?.toString(Charsets.UTF_8)

    operator fun set(uri: Uri, content: String) {
        documents[uri] = content.toByteArray(Charsets.UTF_8)
    }

    override fun openForWriting(uri: Uri): OutputStream = object : ByteArrayOutputStream() {
        override fun close() {
            documents[uri] = toByteArray()
        }
    }

    override fun openForReading(uri: Uri): InputStream? =
        documents[uri]?.let(::ByteArrayInputStream)
}
