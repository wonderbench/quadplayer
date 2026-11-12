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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuadPlaybackEngineTest {
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
            engine.remove(0)
            engine.togglePlayAll()
            assertFalse(engine.hasController(0))
            assertTrue(engine.hasController(1))
        } finally { engine.release(); file.delete() }
    }
}
