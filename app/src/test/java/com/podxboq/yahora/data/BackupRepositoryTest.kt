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
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private lateinit var database: YahoraDatabase
    private lateinit var repository: BackupRepository
    private val files = InMemoryBackupFiles()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        ).build()
        repository = BackupRepository(
            database.backupDao(),
            files,
            now = { EXPORTED_AT },
            io = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a restore on another phone brings back tags, favorites and entries`() = runTest {
        val coffee = database.tagDao().insert(
            Tag(name = "Coffee", isFavorite = true, color = 0xFF6D4C41.toInt()),
        )
        val tea = database.tagDao().insert(Tag(name = "Tea"))
        database.entryDao().insert(Entry(tagId = coffee, timestamp = 100))
        database.entryDao().insert(Entry(tagId = tea, timestamp = 200))
        database.entryDao().insert(Entry(tagId = coffee, timestamp = 300))

        assertEquals(SaveBackupResult.Saved(entryCount = 3), repository.save(FILE))

        // A new phone: same file, empty database.
        val other = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YahoraDatabase::class.java,
        ).build()
        val newPhone = BackupRepository(other.backupDao(), files, io = Dispatchers.Unconfined)
        val backup = (newPhone.read(FILE) as ReadBackupResult.Valid).backup
        assertEquals(EXPORTED_AT, backup.exportedAt)

        assertEquals(RestoreBackupResult.Restored(entryCount = 3), newPhone.restore(backup))

        val tags = other.tagDao().observeAll().first()
        assertEquals(listOf("Coffee" to true, "Tea" to false), tags.map { it.name to it.isFavorite })
        assertEquals(listOf(0xFF6D4C41.toInt(), null), tags.map { it.color })
        assertEquals(listOf(300L, 100L), other.entryDao().observeForTag(coffee).first().map { it.timestamp })
        assertEquals(listOf(200L), other.entryDao().observeForTag(tea).first().map { it.timestamp })
        other.close()
    }

    @Test
    fun `restoring replaces the log rather than adding to it`() = runTest {
        val coffee = database.tagDao().insert(Tag(name = "Coffee"))
        database.entryDao().insert(Entry(tagId = coffee, timestamp = 100))
        repository.save(FILE)

        database.entryDao().insert(Entry(tagId = coffee, timestamp = 200))
        database.tagDao().insert(Tag(name = "Tea"))

        val backup = (repository.read(FILE) as ReadBackupResult.Valid).backup
        repository.restore(backup)

        assertEquals(listOf("Coffee"), database.tagDao().observeAll().first().map { it.name })
        assertEquals(1, database.entryDao().countForTag(coffee))
    }

    @Test
    fun `after a restore, tags and taps carry on as before`() = runTest {
        val coffee = database.tagDao().insert(Tag(name = "Coffee"))
        repository.save(FILE)
        repository.restore((repository.read(FILE) as ReadBackupResult.Valid).backup)

        // The unique name index and the id sequence survived the replacement.
        val tags = TagRepository(database.tagDao())
        assertEquals(AddTagResult.DuplicateName, tags.addTag("coffee"))
        assertTrue(tags.addTag("Tea") is AddTagResult.Created)
        val logged = EntryRepository(database.entryDao()).logEntry(coffee)
        assertTrue(logged is LogEntryResult.Logged)
    }

    @Test
    fun `a document that cannot be opened is unreadable`() = runTest {
        assertEquals(
            ReadBackupResult.Invalid(BackupProblem.UNREADABLE),
            repository.read(Uri.parse("content://test/missing.json")),
        )
    }

    @Test
    fun `a document that cannot be written fails the save`() = runTest {
        val refusing = object : BackupFiles by files {
            override fun openForWriting(uri: Uri) = null
        }
        val repository = BackupRepository(database.backupDao(), refusing, io = Dispatchers.Unconfined)

        assertEquals(SaveBackupResult.Failed, repository.save(FILE))
    }

    private companion object {
        const val EXPORTED_AT = 1_700_000_000_000L
        val FILE: Uri = Uri.parse("content://test/yahora.json")
    }
}
