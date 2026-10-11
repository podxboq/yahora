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

import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId

/**
 * Where backup files are read from and written to. The app hands it the
 * documents the user picked; tests hand it memory.
 */
interface BackupFiles {
    /** Null if the document cannot be opened. */
    fun openForWriting(uri: Uri): OutputStream?

    /** Null if the document cannot be opened. */
    fun openForReading(uri: Uri): InputStream?
}

/** The documents the system file picker grants access to — no permission needed. */
class ContentResolverBackupFiles(private val resolver: ContentResolver) : BackupFiles {
    // "wt" truncates: overwriting a longer, older backup must not leave its tail behind.
    override fun openForWriting(uri: Uri): OutputStream? = resolver.openOutputStream(uri, "wt")

    override fun openForReading(uri: Uri): InputStream? = resolver.openInputStream(uri)
}

/** Outcome of writing a backup. */
sealed interface SaveBackupResult {
    data class Saved(val entryCount: Int) : SaveBackupResult
    data object Failed : SaveBackupResult
}

/** Outcome of reading a file that is meant to be restored. */
sealed interface ReadBackupResult {
    data class Valid(val backup: Backup) : ReadBackupResult
    data class Invalid(val problem: BackupProblem) : ReadBackupResult
}

/** Outcome of putting a backup in place of the current log. */
sealed interface RestoreBackupResult {
    data class Restored(val entryCount: Int) : RestoreBackupResult

    /** The database refused it, and the transaction left the log as it was. */
    data object Failed : RestoreBackupResult
}

/**
 * Saving the log to a file and putting it back, so it survives losing the
 * phone, changing it, or a mistake in the app itself.
 *
 * Restoring is split in two — [read], then [restore] — because between them
 * the user is asked to confirm, and what they confirm should be the file
 * already checked, not a promise to check it later.
 *
 * @param now the clock, for the export time and the suggested file name.
 * @param io where file work runs; tests pass their own dispatcher.
 */
class BackupRepository(
    private val backupDao: BackupDao,
    private val files: BackupFiles,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** A dated name, so successive backups sit side by side instead of overwriting. */
    fun suggestedFileName(): String {
        val date = Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate()
        return "yahora-$date.json"
    }

    suspend fun save(uri: Uri): SaveBackupResult {
        val (tags, entries) = backupDao.snapshot()
        val backup = Backup(tags = tags, entries = entries, exportedAt = now())
        return withContext(io) {
            try {
                val output = files.openForWriting(uri) ?: return@withContext SaveBackupResult.Failed
                output.use { BackupFormat.write(backup, it) }
                SaveBackupResult.Saved(entries.size)
            } catch (_: IOException) {
                SaveBackupResult.Failed
            } catch (_: SecurityException) {
                SaveBackupResult.Failed
            }
        }
    }

    /** Reads and checks [uri]. Touches nothing: the log is replaced by [restore]. */
    suspend fun read(uri: Uri): ReadBackupResult = withContext(io) {
        try {
            val input = files.openForReading(uri)
                ?: return@withContext ReadBackupResult.Invalid(BackupProblem.UNREADABLE)
            ReadBackupResult.Valid(input.use(BackupFormat::read))
        } catch (e: InvalidBackupException) {
            ReadBackupResult.Invalid(e.problem)
        } catch (_: IOException) {
            ReadBackupResult.Invalid(BackupProblem.UNREADABLE)
        } catch (_: SecurityException) {
            ReadBackupResult.Invalid(BackupProblem.UNREADABLE)
        }
    }

    /** Replaces the whole log with [backup] — all of it, or none of it. */
    suspend fun restore(backup: Backup): RestoreBackupResult =
        try {
            // Tags are rebuilt so name_key is derived here, never taken on trust.
            backupDao.replaceAll(
                tags = backup.tags.map {
                    Tag(id = it.id, name = it.name, isFavorite = it.isFavorite, color = it.color)
                },
                entries = backup.entries.map { Entry(tagId = it.tagId, timestamp = it.timestamp) },
            )
            RestoreBackupResult.Restored(backup.entries.size)
        } catch (_: SQLiteException) {
            RestoreBackupResult.Failed
        }
}
