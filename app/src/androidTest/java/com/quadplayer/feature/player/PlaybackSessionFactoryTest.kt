package com.quadplayer.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.data.*
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.database.VideoEntity
import com.quadplayer.core.model.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackSessionFactoryTest {
    private class FakeSinglePlayer : SinglePlayerHandle {
        override val uiState = MutableStateFlow(PlayerUiState())
        override var onPlayerRebuilt: ((Player) -> Unit)? = null
        val loaded = mutableListOf<Long>()
        var released = false
        private var playing = false
        private var position = 0L
        override val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getDuration" -> 10_000L
                "getCurrentPosition" -> position
                "getPlaybackState" -> Player.STATE_READY
                "getPlayWhenReady" -> playing
                "toString" -> "FakeSinglePlayer"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
        override suspend fun applySettings(settings: AppSettings) = Unit
        override suspend fun load(video: VideoFile, startPositionMs: Long, playWhenReady: Boolean): Boolean {
            loaded += video.id
            position = startPositionMs
            playing = playWhenReady
            uiState.value = PlayerUiState(playbackState = PlaybackState.READY, playWhenReady = playWhenReady, durationMs = 10_000)
            return true
        }
        override fun setProgressUpdatesEnabled(enabled: Boolean) = Unit
        override fun setPlaybackSpeed(speed: PlaybackSpeed) = Unit
        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun seekTo(positionMs: Long) { position = positionMs }
        override fun setLoopStart() = false
        override fun setLoopEnd() = false
        override fun clearLoop() = Unit
        override fun retry(playWhenReady: Boolean) { playing = playWhenReady }
        override fun release() { released = true }
        override fun togglePlayPause() { playing = !playing }
        override fun seekBy(deltaMs: Long) { position += deltaMs }
        override fun cycleAspectMode() = Unit
        override fun selectAudioTrack(option: TrackOption?) = Unit
        override fun selectSubtitleTrack(option: TrackOption?) = Unit
        override suspend fun setExternalSubtitle(uri: Uri?) = Unit
        override suspend fun setExternalAudio(uri: Uri?) = Unit
    }

    @Test fun queueAdvancesUsingAnInjectedPlayerWithoutADecoder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        val store = ViewModelStore()
        var sessionJob: Job? = null
        val fake = FakeSinglePlayer()
        val settings = object : SettingsRepository {
            override val settings = flowOf(AppSettings(autoResume = false))
            override suspend fun setDecoderMode(mode: DecoderMode) = Unit
            override suspend fun setDefaultPlaybackSpeed(speed: PlaybackSpeed) = Unit
            override suspend fun setDoubleTapSeekSeconds(seconds: Int) = Unit
            override suspend fun setReverseDoubleTapSeek(enabled: Boolean) = Unit
            override suspend fun setBackgroundPlayback(enabled: Boolean) = Unit
            override suspend fun setAutoResume(enabled: Boolean) = Unit
            override suspend fun setCrossfadeAudioSwitching(enabled: Boolean) = Unit
            override suspend fun setThemeMode(themeMode: AppThemeMode) = Unit
            override suspend fun setSubtitleStyle(style: SubtitleStyle) = Unit
            override suspend fun setAutoPictureInPicture(enabled: Boolean) = Unit
        }
        val history = HistoryWriteQueue(DefaultHistoryRepository(db.playHistoryDao()))
        try {
            val ids = (1..2).map { index -> db.videoDao().upsert(VideoEntity(uriString = "content://test/$index", displayName = "v$index", folderPath = "test", folderName = "test", durationMs = 10_000, sizeBytes = 1, width = 1, height = 1, mimeType = "video/mp4", dateModified = 0)) }
            val videos = DefaultVideoRepository(db.videoDao(), MediaStoreVideoScanner(context), db)
            val playlists = DefaultPlaylistRepository(db.playlistDao(), db, context)
            val playlist = playlists.createPlaylistWithVideos("test", ids)
            var factoryCalls = 0
            val session = withContext(Dispatchers.Main) {
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaybackSession(
                        context, SavedStateHandle(mapOf("videoId" to ids[0], "playlistId" to playlist)),
                        videos, playlists, history, settings, playerFactory = { factoryCalls++; fake },
                    ) as T
                }
                ViewModelProvider(store, factory)[PlaybackSession::class.java].also {
                    sessionJob = it.viewModelScope.coroutineContext[Job]
                }
            }
            withTimeout(10_000) { session.state.first { it.video?.id == ids[0] } }
            assertSame(fake, session.controller)
            withContext(Dispatchers.Main) { fake.uiState.value = fake.uiState.value.copy(playbackState = PlaybackState.ENDED) }
            withTimeout(10_000) { session.state.first { it.video?.id == ids[1] } }
            assertEquals(ids, fake.loaded)
            assertEquals(1, factoryCalls)
        } finally {
            try {
                withContext(Dispatchers.Main) { store.clear() }
                sessionJob?.join()
                history.getResumePosition(1L) // drain queued history writes before closing the database
            } finally { db.close() }
        }
        assertTrue(fake.released)
    }
}
