package com.quadplayer.feature.quadplayer

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.VideoFile
import com.quadplayer.feature.player.PlayerUiState
import com.quadplayer.feature.player.QuadPlayerHandle
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuadPlaybackEngineFakeTest {
    private class FakeHandle : QuadPlayerHandle {
        override val uiState = MutableStateFlow(PlayerUiState())
        var playing = false
        var position = 0L
        var released = false
        var volume = 0f
        var speed = 1f
        private var repeat = Player.REPEAT_MODE_OFF
        override val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getPlayWhenReady" -> playing
                "getCurrentPosition" -> position
                "getDuration" -> 10_000L
                "isCurrentMediaItemSeekable" -> true
                "getPlaybackState" -> Player.STATE_READY
                "getPlayerError" -> null
                "getPlaybackParameters" -> PlaybackParameters(speed)
                "getVolume" -> volume
                "setVolume" -> { volume = args!![0] as Float; null }
                "getRepeatMode" -> repeat
                "setRepeatMode" -> { repeat = args!![0] as Int; null }
                "seekToDefaultPosition" -> { position = 0L; null }
                "toString" -> "FakePlayer"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
        override suspend fun applySettings(settings: AppSettings) = Unit
        override suspend fun load(video: VideoFile, startPositionMs: Long, playWhenReady: Boolean): Boolean { position = startPositionMs; playing = playWhenReady; return true }
        override fun setProgressUpdatesEnabled(enabled: Boolean) = Unit
        override fun setPlaybackSpeed(speed: PlaybackSpeed) { this.speed = speed.value }
        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun seekTo(positionMs: Long) { position = positionMs }
        override fun setLoopStart() = true
        override fun setLoopEnd() = true
        override fun clearLoop() = Unit
        override fun retry(playWhenReady: Boolean) { playing = playWhenReady }
        override fun release() { released = true }
    }

    @Test fun swapKeepsHandlesAndSyncRestoresEachPlayIntent() = runTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val made = mutableListOf<FakeHandle>()
        val engine = QuadPlaybackEngine(controllerFactory = { FakeHandle().also(made::add) }, awaitReadyOverride = { true })
        fun video(id: Long) = VideoFile(id, "content://test/$id", "test$id", "test", "test", 10_000, 0, 0, 0, "video/mp4", 0, false, true)
        try {
            assertTrue(engine.load(0, video(1)))
            assertTrue(engine.load(1, video(2)))
            engine.seekSlot(0, 1200)
            engine.seekSlot(1, 300)
            engine.setSlotPlaying(1, false)
            engine.swap(0, 1)
            assertSame(made[1], engine.controllerAt(0))
            assertSame(made[0], engine.controllerAt(1))
            assertFalse(made[0].released)
            assertFalse(made[1].released)
            var result: Boolean? = null
            assertTrue(engine.synchronize(1) { result = it })
            runCurrent()
            assertEquals(true, result)
            assertEquals(1200L, made[1].position)
            assertTrue(made[0].playing)
            assertFalse(made[1].playing)
            engine.remove(0)
            assertTrue(made[1].released)
            assertFalse(made[0].released)
        } finally { engine.release(); Dispatchers.resetMain() }
    }

    @Test fun interruptedSyncCannotRestartFakePlayers() = runTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val made = mutableListOf<FakeHandle>()
        val ready = CompletableDeferred<Boolean>()
        val engine = QuadPlaybackEngine(controllerFactory = { FakeHandle().also(made::add) }, awaitReadyOverride = { ready.await() })
        fun video(id: Long) = VideoFile(id, "content://test/$id", "test$id", "test", "test", 10_000, 0, 0, 0, "video/mp4", 0, false, true)
        try {
            engine.load(0, video(1)); engine.load(1, video(2))
            var callbacks = 0
            assertTrue(engine.synchronize(0) { callbacks++ })
            assertFalse(made[0].playing)
            assertFalse(made[1].playing)
            engine.interruptPlayback()
            ready.complete(true)
            runCurrent()
            assertEquals(0, callbacks)
            assertFalse(made[0].playing)
            assertFalse(made[1].playing)
        } finally { engine.release(); Dispatchers.resetMain() }
    }
}
