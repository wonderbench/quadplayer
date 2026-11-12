package com.quadplayer.feature.player

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.model.VideoFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressPollingTest {
    @Test fun hiddenProgressStopsPollingButSeekUsesLivePosition() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "progress-polling-test.wav")
        val samples = ByteArray(16000 * 2 * 10)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        file.writeBytes(header.array() + samples)
        val video = VideoFile(1, Uri.fromFile(file).toString(), file.name, "test", "test", 10000, file.length(), 0, 0, "audio/wav", 0, false, true)
        val controller = PlayerController(context)
        try {
            controller.load(video)
            withTimeout(10000) { controller.uiState.first { it.isPlaying } }
            controller.setProgressUpdatesEnabled(false)
            delay(700)
            val stale = controller.uiState.value.currentPositionMs
            delay(700)
            val live = controller.player.currentPosition
            assertTrue(live > stale + 300)
            controller.seekBy(1000)
            assertTrue(controller.player.currentPosition >= live + 900)
            controller.pause()
            controller.seekTo(3000)
            withTimeout(5000) { controller.uiState.first { it.currentPositionMs in 2900..3100 } }
            Unit
        } finally { controller.release(); file.delete() }
    }
}
