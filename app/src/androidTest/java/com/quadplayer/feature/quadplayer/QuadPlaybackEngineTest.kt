package com.quadplayer.feature.quadplayer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.model.VideoFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuadPlaybackEngineTest {
    @Test fun repeatIsPerVideoFollowsSwapAndReallyLoops() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "quad-repeat.wav")
        val samples = ByteArray(16000 * 2)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        file.writeBytes(header.array() + samples)
        fun video(id: Long) = VideoFile(id, Uri.fromFile(file).toString(), "test$id", "test", "test", 1000, file.length(), 0, 0, "audio/wav", 0, false, true)
        val engine = QuadPlaybackEngine(context)
        try {
            engine.load(0, video(1)); engine.load(1, video(2))
            engine.setSlotPlaying(0, false); engine.setSlotPlaying(1, false)
            engine.setSlotRepeat(0, true)
            val repeating = engine.controllerAt(0)!!.player
            val looped = CompletableDeferred<Unit>()
            repeating.addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) looped.complete(Unit)
                }
            })
            withTimeout(10000) { while (!repeating.isCurrentMediaItemSeekable) delay(50) }
            engine.swap(0, 1)
            assertFalse(engine.slotStates(flowOf(-1)).first()[0].repeatOne)
            assertTrue(engine.slotStates(flowOf(-1)).first()[1].repeatOne)
            repeating.seekTo(800)
            engine.setSlotPlaying(1, true)
            withTimeout(10000) { looped.await() }
            engine.setSlotRepeat(1, false)
            assertEquals(Player.REPEAT_MODE_OFF, repeating.repeatMode)
            engine.setSlotRepeat(1, true)
            engine.load(1, video(3))
            assertEquals(Player.REPEAT_MODE_OFF, engine.controllerAt(1)!!.player.repeatMode)
        } finally { engine.release(); file.delete() }
    }

    @Test fun linkedSeekMovesRealPlayersWithoutChangingPauseState() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "quad-linked-seek.wav")
        val samples = ByteArray(16000 * 2 * 5)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        file.writeBytes(header.array() + samples)
        fun video(id: Long) = VideoFile(id, Uri.fromFile(file).toString(), "test$id", "test", "test", 5000, file.length(), 0, 0, "audio/wav", 0, false, true)
        val engine = QuadPlaybackEngine(context)
        try {
            engine.load(0, video(1)); engine.load(1, video(2))
            engine.controllerAt(0)!!.pause(); engine.controllerAt(1)!!.pause()
            withTimeout(10000) {
                while (!engine.controllerAt(0)!!.player.isCurrentMediaItemSeekable || !engine.controllerAt(1)!!.player.isCurrentMediaItemSeekable) delay(50)
            }
            engine.seekSlot(0, 1000); engine.seekSlot(1, 2000)
            assertEquals(false, engine.seekLinked(0, 2500))
            assertEquals(2500L, engine.currentPosition(0))
            assertEquals(3500L, engine.currentPosition(1))
            assertFalse(engine.controllerAt(0)!!.player.playWhenReady)
            assertFalse(engine.controllerAt(1)!!.player.playWhenReady)
            val secondDuration = engine.controllerAt(1)!!.player.duration
            assertEquals(true, engine.seekLinked(0, 5000))
            assertEquals(secondDuration - 1000L, engine.currentPosition(0))
            // ExoPlayer can clamp a seek at the exact end to the final sample.
            assertTrue(kotlin.math.abs(secondDuration - engine.currentPosition(1)!!) <= 1L)
        } finally { engine.release(); file.delete() }
    }

    @Test fun removingOrReplacingWhileLoadSuspendsNeverRestoresStaleSlot() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "quad-stale-load.wav").apply { writeBytes(ByteArray(44)) }
        fun video(id: Long) = VideoFile(id, Uri.fromFile(file).toString(), "test$id", "test", "test", 5000, file.length(), 0, 0, "audio/wav", 0, false, true)
        var entered = CompletableDeferred<Unit>()
        var resume = CompletableDeferred<Unit>()
        val engine = QuadPlaybackEngine(context) { candidate ->
            if (candidate.id == 2L || candidate.id == 3L) { entered.complete(Unit); resume.await() }
        }
        try {
            engine.load(0, video(1))
            val removedLoad = async { engine.load(0, video(2)) }
            entered.await()
            engine.remove(0)
            resume.complete(Unit)
            assertFalse(removedLoad.await())
            assertFalse(engine.hasController(0))

            engine.load(0, video(1))
            entered = CompletableDeferred()
            resume = CompletableDeferred()
            val staleLoad = async { engine.load(0, video(3)) }
            entered.await()
            assertTrue(engine.load(0, video(4)))
            resume.complete(Unit)
            assertFalse(staleLoad.await())
            assertEquals(4L, engine.slotStates(flowOf(-1)).first()[0].video?.id)
        } finally { engine.release(); file.delete() }
    }

    @Test fun backgroundPausesAndRestoresOnlyPriorPlayIntentsIncludingLateImports() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "quad-lifecycle-test.wav").apply { writeBytes(ByteArray(44)) }
        fun video(id: Long) = VideoFile(id, Uri.fromFile(file).toString(), "test$id", "test", "test", 5000, file.length(), 0, 0, "audio/wav", 0, false, true)
        val engine = QuadPlaybackEngine(context)
        try {
            engine.load(0, video(1))
            engine.load(1, video(2))
            engine.setSlotPlaying(1, false)
            engine.suspendPlayback()
            engine.suspendPlayback() // duplicate lifecycle notifications must not erase intent
            assertFalse(engine.controllerAt(0)!!.player.playWhenReady)
            assertFalse(engine.controllerAt(1)!!.player.playWhenReady)
            assertTrue(engine.snapshot()[0]!!.playing)
            assertFalse(engine.snapshot()[1]!!.playing)
            engine.load(2, video(3)) // a picker/import can finish while stopped
            assertFalse(engine.controllerAt(2)!!.player.playWhenReady)
            assertTrue(engine.snapshot()[2]!!.playing)
            engine.swap(0, 1)
            engine.resumePlayback()
            assertFalse(engine.controllerAt(0)!!.player.playWhenReady)
            assertTrue(engine.controllerAt(1)!!.player.playWhenReady)
            assertTrue(engine.controllerAt(2)!!.player.playWhenReady)
            engine.suspendPlayback()
            engine.interruptPlayback() // also cancels pending foreground resumes
            engine.load(3, video(4)) // an in-flight load cannot restart after an interruption
            assertFalse(engine.snapshot()[3]!!.playing)
            engine.resumePlayback()
            assertFalse(engine.controllerAt(1)!!.player.playWhenReady)
            assertFalse(engine.controllerAt(2)!!.player.playWhenReady)
        } finally { engine.release(); file.delete() }
    }

    @Test fun swapCarriesPositionAndPlaybackIntentAndRemovalClearsMedia() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "quad-test.wav")
        val samples = ByteArray(16000 * 2 * 5)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        file.writeBytes(header.array() + samples)
        fun video(id: Long) = VideoFile(id, Uri.fromFile(file).toString(), "test$id", "test", "test", 5000, file.length(), 0, 0, "audio/wav", 0, false, true)
        val engine = QuadPlaybackEngine(context)
        try {
            assertFalse(engine.hasController(0))
            assertFalse(engine.hasController(1))
            engine.load(0, video(1)); engine.load(1, video(2))
            engine.controllerAt(0)!!.pause()
            engine.controllerAt(0)!!.seekTo(1000)
            engine.controllerAt(1)!!.seekTo(2000)
            engine.swap(0, 1)
            assertTrue(engine.controllerAt(0)!!.player.playWhenReady)
            assertFalse(engine.controllerAt(1)!!.player.playWhenReady)
            assertEquals(2000L, engine.controllerAt(0)!!.player.currentPosition)
            assertEquals(1000L, engine.controllerAt(1)!!.player.currentPosition)
            engine.controllerAt(1)!!.seekTo(5000)
            engine.controllerAt(1)!!.play()
            withTimeout(10000) {
                while (engine.controllerAt(1)!!.player.playbackState != Player.STATE_ENDED) delay(50)
            }
            engine.toggleSlot(1)
            assertTrue(engine.controllerAt(1)!!.player.playWhenReady)
            assertTrue(engine.controllerAt(1)!!.player.currentPosition < 5000)
            engine.remove(0)
            engine.togglePlayAll()
            assertFalse(engine.hasController(0))
            assertTrue(engine.hasController(1))
        } finally { engine.release(); file.delete() }
    }
}
