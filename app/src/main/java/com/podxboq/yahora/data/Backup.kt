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

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * The whole log as one value: what a backup file holds, and what a restore puts
 * back. Entries point at their tag by [Tag.id], and a restore keeps those ids,
 * so the file needs no other way of linking the two.
 */
data class Backup(
    val tags: List<Tag>,
    val entries: List<Entry>,
    /** When the file was written, in epoch milliseconds. */
    val exportedAt: Long,
)

/** Why a file could not be taken as a backup. */
enum class BackupProblem {
    /** It is not a Yahora backup at all — some other file was picked. */
    NOT_A_BACKUP,

    /** Written by a later version of the app, in a format this one cannot read. */
    NEWER_VERSION,

    /** It claims to be a backup, but what it holds does not add up. */
    DAMAGED,

    /** The file could not be opened or read. */
    UNREADABLE,
}

class InvalidBackupException(val problem: BackupProblem, message: String) : Exception(message)

/**
 * The backup file: the app's own JSON, meant for restoring, not for editing.
 *
 * A log that can be rewritten by hand stops being evidence of anything, so the
 * format makes no effort to be a spreadsheet. What it does insist on is being
 * checked whole before a restore touches the database: a file that would leave
 * the log half-replaced is refused up front.
 */
object BackupFormat {

    /** Tells a Yahora backup apart from any other JSON file. */
    const val MARKER = "yahora-backup"

    /**
     * Bumped whenever the shape changes. A version may read every earlier one;
     * a later one is refused rather than half-understood.
     */
    const val VERSION = 1

    /**
     * Largest file read into memory. Far beyond any real log — a million entries
     * fit — and it keeps picking a video by mistake from exhausting the heap.
     */
    private const val MAX_BYTES = 64 * 1024 * 1024

    /** Writes [backup] to [output], leaving the stream open: the caller owns it. */
    fun write(backup: Backup, output: OutputStream) {
        val tags = JSONArray()
        backup.tags.forEach { tag ->
            tags.put(
                JSONObject()
                    .put("id", tag.id)
                    .put("name", tag.name)
                    .put("favorite", tag.isFavorite),
            )
        }
        val entries = JSONArray()
        backup.entries.forEach { entry ->
            entries.put(JSONObject().put("tag", entry.tagId).put("timestamp", entry.timestamp))
        }
        val json = JSONObject()
            .put("format", MARKER)
            .put("version", VERSION)
            .put("exportedAt", backup.exportedAt)
            .put("tags", tags)
            .put("entries", entries)

        val writer = output.bufferedWriter(Charsets.UTF_8)
        writer.write(json.toString())
        writer.flush()
    }

    /**
     * Reads and checks a backup.
     *
     * @throws InvalidBackupException if [input] is not a backup this version can
     *   restore in full.
     */
    fun read(input: InputStream): Backup {
        val bytes = readAtMost(input, MAX_BYTES)

        val json = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: JSONException) {
            fail(BackupProblem.NOT_A_BACKUP, "not a JSON object")
        }
        if (json.optString("format") != MARKER) fail(BackupProblem.NOT_A_BACKUP, "no format marker")

        val version = json.optInt("version", 0)
        if (version > VERSION) fail(BackupProblem.NEWER_VERSION, "version $version")
        if (version < 1) fail(BackupProblem.DAMAGED, "version $version")

        val backup = try {
            Backup(
                tags = json.getJSONArray("tags").objects().map { tag ->
                    Tag(
                        id = tag.getLong("id"),
                        name = tag.getString("name"),
                        isFavorite = tag.getBoolean("favorite"),
                    )
                },
                entries = json.getJSONArray("entries").objects().map { entry ->
                    Entry(tagId = entry.getLong("tag"), timestamp = entry.getLong("timestamp"))
                },
                exportedAt = json.getLong("exportedAt"),
            )
        } catch (e: JSONException) {
            fail(BackupProblem.DAMAGED, e.message ?: "missing field")
        }
        check(backup)
        return backup
    }

    /**
     * The rules the database would enforce, checked before it is asked to: a
     * restore that failed halfway would be rolled back, but refusing the file
     * outright can say why.
     */
    private fun check(backup: Backup) {
        val ids = HashSet<Long>()
        val keys = HashSet<String>()
        backup.tags.forEach { tag ->
            if (tag.id <= 0 || !ids.add(tag.id)) fail(BackupProblem.DAMAGED, "tag id ${tag.id}")
            if (tag.name.isBlank()) fail(BackupProblem.DAMAGED, "blank tag name")
            if (TagName.lengthOf(tag.name) > TagName.MAX_LENGTH) {
                fail(BackupProblem.DAMAGED, "tag name too long")
            }
            if (!keys.add(tag.nameKey)) fail(BackupProblem.DAMAGED, "duplicate tag ${tag.name}")
        }
        backup.entries.forEach { entry ->
            if (entry.tagId !in ids) fail(BackupProblem.DAMAGED, "entry for tag ${entry.tagId}")
        }
    }

    /**
     * The whole stream, refusing it once it passes [limit]. Written out because
     * `InputStream.readNBytes` only arrived in API 33.
     */
    private fun readAtMost(input: InputStream, limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) return buffer.toByteArray()
            buffer.write(chunk, 0, read)
            if (buffer.size() > limit) fail(BackupProblem.NOT_A_BACKUP, "larger than $limit bytes")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun fail(problem: BackupProblem, detail: String): Nothing =
        throw InvalidBackupException(problem, detail)
}
