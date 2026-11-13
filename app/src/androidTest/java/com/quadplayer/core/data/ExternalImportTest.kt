package com.quadplayer.core.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.database.VideoEntity
import com.quadplayer.core.database.PlayHistoryEntity
import com.quadplayer.core.database.PlaylistEntity
import com.quadplayer.core.database.PlaylistVideoEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalImportTest {
    @Test fun refreshHidesMissingManualFilesWithoutUnhidingUserRemovedItems() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val missing = File(context.cacheDir, "manual-stale-test.mp4").apply { writeBytes(ByteArray(64)) }
        val hidden = File(context.cacheDir, "manual-hidden-test.mp4").apply { writeBytes(ByteArray(64)) }
        val database = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        try {
            val dao = database.videoDao()
            val stale = VideoEntity(id = 42, uriString = Uri.fromFile(missing).toString(), displayName = "stale", folderPath = "imported", folderName = "imported", durationMs = 100, sizeBytes = 64, width = 0, height = 0, mimeType = "video/mp4", dateModified = 0, isFavorite = true, isManuallyAdded = true)
            dao.upsert(stale)
            dao.upsert(stale.copy(id = 43, uriString = Uri.fromFile(hidden).toString(), displayName = "hidden", isHidden = true))
            database.playHistoryDao().upsert(PlayHistoryEntity(videoId = 42, lastPositionMs = 75, lastPlayedAt = 1))
            missing.delete()
            val repository = DefaultVideoRepository(dao, MediaStoreVideoScanner(context), database)
            repository.refreshVideos()
            assertFalse(dao.getById(42)!!.isAccessible)
            assertFalse(dao.getById(42)!!.isHidden)
            assertTrue(dao.getById(43)!!.isAccessible)
            assertTrue(dao.getById(43)!!.isHidden)
            assertTrue(repository.videos.first().none { it.id == 42L || it.id == 43L })
            assertEquals(75L, database.playHistoryDao().getByVideoId(42)!!.lastPositionMs)
            missing.writeBytes(ByteArray(64))
            repository.refreshVideos()
            assertTrue(dao.getById(42)!!.isAccessible)
            assertTrue(dao.getById(43)!!.isHidden)
            assertEquals(42L, repository.importVideo(Uri.fromFile(missing))!!.id)
            assertTrue(dao.getById(42)!!.isAccessible)
            assertTrue(dao.getById(42)!!.isFavorite)
            assertEquals(75L, database.playHistoryDao().getByVideoId(42)!!.lastPositionMs)
            assertTrue(dao.getById(43)!!.isHidden)
        } finally { database.close(); missing.delete(); hidden.delete() }
    }

    @Test fun hidingManualImportPreservesReferencesAndReimportRestoresIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "removable-manual-import.mp4").apply { writeBytes(ByteArray(64)) }
        val uri = Uri.fromFile(file)
        val database = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        try {
            val dao = database.videoDao()
            val manual = VideoEntity(id = 42, uriString = uri.toString(), displayName = "manual", folderPath = "imported", folderName = "imported", durationMs = 100, sizeBytes = 64, width = 0, height = 0, mimeType = "video/mp4", dateModified = 0, isFavorite = true, isManuallyAdded = true)
            dao.upsert(manual)
            dao.upsert(manual.copy(id = 43, uriString = "content://media/external/video/media/43", isManuallyAdded = false))
            database.playHistoryDao().upsert(PlayHistoryEntity(videoId = 42, lastPositionMs = 75, lastPlayedAt = 1))
            database.playlistDao().insertPlaylist(PlaylistEntity(id = 1, name = "test", createdAt = 1, updatedAt = 1))
            database.playlistDao().upsertEntries(listOf(PlaylistVideoEntity(1, 42, 0)))
            val repository = DefaultVideoRepository(dao, MediaStoreVideoScanner(context), database)
            assertFalse(repository.hideManualVideo(43, "content://media/external/video/media/43"))
            assertFalse(repository.hideManualVideo(42, "content://unrelated"))
            val old = DocumentAccessGrants.capture(uri)
            DocumentAccessGrants.noteSelection(uri)
            assertFalse(repository.hideManualVideo(42, uri.toString(), old.revision))
            assertFalse(dao.getById(42)!!.isHidden)
            val current = DocumentAccessGrants.capture(uri)
            assertTrue(repository.hideManualVideo(42, uri.toString(), current.revision))
            assertFalse(repository.hideManualVideo(42, uri.toString()))
            assertEquals(listOf(43L), repository.videos.first().map { it.id })
            assertEquals(75L, database.playHistoryDao().getByVideoId(42)!!.lastPositionMs)
            assertTrue(dao.getById(42)!!.isFavorite)
            assertEquals(42L, database.playlistDao().getPlaylistEntries(1).single().videoId)
            assertTrue(database.playlistDao().observePlaylistVideos(1).first().isEmpty())
            assertTrue(file.exists())
            assertEquals(42L, repository.importVideo(uri)!!.id)
            assertTrue(dao.getById(42)!!.isAccessible)
            assertEquals(75L, database.playHistoryDao().getByVideoId(42)!!.lastPositionMs)
            assertEquals(42L, database.playlistDao().observePlaylistVideos(1).first().single().id)
        } finally { database.close(); file.delete() }
    }

    @Test fun reauthorizationRestoresExistingRecordButUnreadableUriIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "external-import-test.mp4").apply { writeBytes(ByteArray(64)) }
        val uri = Uri.fromFile(file)
        val database = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        try {
            val dao = database.videoDao()
            val original = VideoEntity(id = 42, uriString = uri.toString(), displayName = "test", folderPath = "test", folderName = "test", durationMs = 100, sizeBytes = 64, width = 0, height = 0, mimeType = "video/mp4", dateModified = 0, isFavorite = true, isAccessible = false)
            dao.upsert(original)
            val repository = DefaultVideoRepository(dao, MediaStoreVideoScanner(context), database)
            assertEquals(42L, repository.importVideo(uri)?.id)
            assertTrue(dao.getById(42)!!.isAccessible)
            assertTrue(dao.getById(42)!!.isFavorite)
            file.delete()
            assertNull(repository.importVideo(uri))
        } finally {
            database.close()
            file.delete()
        }
    }
}
