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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

/** org.json is part of Android, so even the format needs Robolectric. */
@RunWith(RobolectricTestRunner::class)
class BackupFormatTest {

    @Test
    fun `what is written reads back the same`() {
        val backup = Backup(
            tags = listOf(Tag(id = 3, name = "Café", isFavorite = true), Tag(id = 7, name = "Tea 🍵")),
            entries = listOf(
                Entry(tagId = 3, timestamp = 1_700_000_000_000),
                Entry(tagId = 7, timestamp = 1_700_000_060_000),
                Entry(tagId = 3, timestamp = 1_700_000_120_000),
            ),
            exportedAt = 1_700_000_200_000,
        )

        assertEquals(backup, read(write(backup)))
    }

    @Test
    fun `an empty log is a valid backup`() {
        val backup = Backup(tags = emptyList(), entries = emptyList(), exportedAt = 1)

        assertEquals(backup, read(write(backup)))
    }

    @Test
    fun `a file that is not JSON is not a backup`() {
        assertProblem(BackupProblem.NOT_A_BACKUP, "PK\u0003\u0004 a zip, perhaps")
    }

    @Test
    fun `JSON from somewhere else is not a backup`() {
        assertProblem(BackupProblem.NOT_A_BACKUP, """{"tags": [], "entries": []}""")
    }

    @Test
    fun `a backup from a later version is refused rather than half read`() {
        assertProblem(
            BackupProblem.NEWER_VERSION,
            """{"format": "yahora-backup", "version": 2, "something": "new"}""",
        )
    }

    @Test
    fun `a missing field makes the backup damaged`() {
        assertProblem(
            BackupProblem.DAMAGED,
            """{"format": "yahora-backup", "version": 1, "exportedAt": 1, "tags": []}""",
        )
    }

    @Test
    fun `an entry whose tag is not in the file makes it damaged`() {
        assertProblem(
            BackupProblem.DAMAGED,
            backupJson(tags = """{"id": 1, "name": "Coffee", "favorite": false}""", entries = """{"tag": 2, "timestamp": 1}"""),
        )
    }

    @Test
    fun `two tags with the same name make it damaged`() {
        assertProblem(
            BackupProblem.DAMAGED,
            backupJson(
                tags = """{"id": 1, "name": "Coffee", "favorite": false},
                    {"id": 2, "name": " coffee", "favorite": false}""",
            ),
        )
    }

    @Test
    fun `two tags with the same id make it damaged`() {
        assertProblem(
            BackupProblem.DAMAGED,
            backupJson(
                tags = """{"id": 1, "name": "Coffee", "favorite": false},
                    {"id": 1, "name": "Tea", "favorite": false}""",
            ),
        )
    }

    @Test
    fun `a blank tag name makes it damaged`() {
        assertProblem(
            BackupProblem.DAMAGED,
            backupJson(tags = """{"id": 1, "name": "  ", "favorite": false}"""),
        )
    }

    @Test
    fun `a tag name past the limit makes it damaged`() {
        val name = "x".repeat(TagName.MAX_LENGTH + 1)
        assertProblem(
            BackupProblem.DAMAGED,
            backupJson(tags = """{"id": 1, "name": "$name", "favorite": false}"""),
        )
    }

    private fun write(backup: Backup): String =
        ByteArrayOutputStream().also { BackupFormat.write(backup, it) }.toString(Charsets.UTF_8)

    private fun read(text: String): Backup = BackupFormat.read(text.byteInputStream())

    private fun assertProblem(problem: BackupProblem, text: String) {
        val error = assertThrows(InvalidBackupException::class.java) { read(text) }
        assertEquals(problem, error.problem)
    }

    private fun backupJson(tags: String = "", entries: String = "") =
        """{"format": "yahora-backup", "version": 1, "exportedAt": 1,
            "tags": [$tags], "entries": [$entries]}"""
}
