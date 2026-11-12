package com.quadplayer.feature.playlist

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.data.DefaultPlaylistRepository
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.database.VideoEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistManagementTest {
    @Test fun renameReorderByVideoIdAndDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        try {
            val repository = DefaultPlaylistRepository(db.playlistDao(), db)
            val playlist = repository.createPlaylist("Original")
            val ids = (1..2).map { index ->
                db.videoDao().upsert(VideoEntity(uriString = "content://test/$index", displayName = "video$index",
                    folderPath = "test", folderName = "test", durationMs = 1000, sizeBytes = 1,
                    width = 1, height = 1, mimeType = "video/mp4", dateModified = 0))
            }
            ids.forEach { repository.addVideo(playlist, it) }
            val model = withContext(Dispatchers.Main) { PlaylistViewModel(SavedStateHandle(mapOf("playlistId" to playlist)), repository) }
            withTimeout(5000) { model.uiState.first { it.videos.size == 2 && it.name == "Original" } }
            withContext(Dispatchers.Main) { model.moveDown(ids[0]); model.rename("Renamed") }
            val updated = withTimeout(5000) { model.uiState.first { it.name == "Renamed" && it.videos.map { video -> video.id } == ids.reversed() } }
            assertEquals(ids.reversed(), updated.videos.map { it.id })
            val deleted = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) { model.delete { deleted.complete(Unit) } }
            withTimeout(5000) { deleted.await() }
            assertEquals(emptyList<Long>(), repository.playlists.first().map { it.id })
        } finally { db.close() }
    }
}
