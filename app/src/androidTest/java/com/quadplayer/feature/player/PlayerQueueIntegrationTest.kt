package com.quadplayer.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.data.*
import com.quadplayer.core.model.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerQueueIntegrationTest {
    @Test fun middleItemMovesAndEndAdvancesWithSingleRepeatAvailable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "queue-test.wav")
        val samples = ByteArray(16000 * 2 * 10)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        file.writeBytes(header.array() + samples)
        val fixtureVideos = (1L..3L).map { VideoFile(it, Uri.fromFile(file).toString(), "v$it", "test", "test", 10000, file.length(), 0, 0, "audio/wav", 0, false, true) }
        val unavailable = mutableSetOf<Long>()
        val videoRepo = object : VideoRepository {
            override val videos = flowOf(fixtureVideos)
            override val favorites = flowOf(emptyList<VideoFile>())
            override val folders = flowOf(emptyList<FolderInfo>())
            override suspend fun hideManualVideo(videoId: Long, uriString: String, expectedGrantRevision: Long?) = false
            override suspend fun refreshVideos() {}
            override suspend fun importVideo(uri: Uri): VideoFile? = null
            override suspend fun getVideo(videoId: Long) = fixtureVideos.firstOrNull { it.id == videoId && it.id !in unavailable }
            override suspend fun toggleFavorite(videoId: Long) {}
        }
        val playlistRepo = object : PlaylistRepository {
            override val playlists = flowOf(emptyList<PlaylistSummary>())
            override fun observePlaylistVideos(playlistId: Long) = flowOf(fixtureVideos)
            override suspend fun createPlaylist(name: String) = 1L
            override suspend fun renamePlaylist(playlistId: Long, newName: String) {}
            override suspend fun deletePlaylist(playlistId: Long) {}
            override suspend fun addVideo(playlistId: Long, videoId: Long) {}
            override suspend fun removeVideo(playlistId: Long, videoId: Long) {}
            override suspend fun swapVideos(playlistId: Long, firstVideoId: Long, secondVideoId: Long) {}
        }
        val positions = ConcurrentHashMap<Long, Long>()
        val history = object : HistoryRepository {
            override fun observeRecent(limit: Int): Flow<List<RecentPlayback>> = flowOf(emptyList())
            override suspend fun deleteHistory(videoId: Long) { positions.remove(videoId) }
            override suspend fun clearHistory() { positions.clear() }
            override suspend fun getResumePosition(videoId: Long) = positions[videoId] ?: 0L
            override suspend fun recordPlayback(videoId: Long, positionMs: Long) { positions[videoId] = positionMs }
        }
        val historyQueue = HistoryWriteQueue(history)
        val settings = DataStoreSettingsRepository(context)
        settings.setAutoResume(true)
        val store = ViewModelStore()
        suspend fun createViewModel(target: ViewModelStore): PlaybackSession = withContext(Dispatchers.Main) {
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaybackSession(
                    context, SavedStateHandle(mapOf("videoId" to 2L, "playlistId" to 1L)), videoRepo,
                    playlistRepo, historyQueue, settings,
                ) as T
            }
            ViewModelProvider(target, factory)[PlaybackSession::class.java]
        }
        val vm = createViewModel(store)
        try {
            val initial = withTimeout(15000) { vm.state.first { it.video?.id == 2L && it.playerState.durationMs > 0 } }
            assertTrue(initial.queue.hasPrevious && initial.queue.hasNext)
            withContext(Dispatchers.Main) { vm.next() }
            val end = withTimeout(15000) { vm.state.first { it.video?.id == 3L } }
            assertFalse(end.queue.hasNext)
            withContext(Dispatchers.Main) { vm.previous() }
            withTimeout(15000) { vm.state.first { it.video?.id == 2L && it.playerState.durationMs > 0 } }
            withContext(Dispatchers.Main) { vm.toggleRepeatOne() }
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, withContext(Dispatchers.Main) { vm.controller.player.repeatMode })
            withContext(Dispatchers.Main) { vm.toggleRepeatOne(); vm.seekTo(9999) }
            val advanced = withTimeout(15000) { vm.state.first { it.video?.id == 3L } }
            assertEquals(2, advanced.queue.index)
            assertEquals(0L, historyQueue.getResumePosition(2))
            unavailable += 2L
            withContext(Dispatchers.Main) { vm.previous() }
            val skipped = withTimeout(15000) { vm.state.first { it.video?.id == 1L && it.queue.index == 0 } }
            assertFalse(skipped.queue.hasPrevious)
            withContext(Dispatchers.Main) { store.clear() }
            unavailable.clear()
            val reopenedStore = ViewModelStore()
            try {
                val reopened = createViewModel(reopenedStore)
                withTimeout(15000) { reopened.state.first { it.video?.id == 2L && it.playerState.durationMs > 0 } }
                delay(750)
                assertEquals(2L, reopened.state.value.video?.id)
                assertTrue(reopened.state.value.playerState.currentPositionMs < 10000)
            } finally { withContext(Dispatchers.Main) { reopenedStore.clear() } }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            file.delete()
        }
    }
}
