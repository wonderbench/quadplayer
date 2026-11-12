package com.quadplayer.feature.player

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.model.VideoFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackFailureIntegrationTest {
    @Test fun missingFileProducesActionableFailure() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val missing = File(context.cacheDir, "quad-missing-${System.nanoTime()}.mp4")
        val video = VideoFile(1, Uri.fromFile(missing).toString(), "missing", "test", "test", 0, 0, 0, 0, "video/mp4", 0, false, true)
        val controller = PlayerController(context)
        try {
            controller.load(video)
            withTimeout(10_000) { while (controller.uiState.value.errorMessage == null) delay(50) }
            assertEquals(PlaybackFailure.MISSING, controller.uiState.value.failure)
        } finally { controller.release() }
    }
}
