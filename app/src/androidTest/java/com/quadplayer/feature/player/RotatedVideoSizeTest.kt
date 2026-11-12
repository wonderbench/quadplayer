package com.quadplayer.feature.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quadplayer.core.model.VideoFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RotatedVideoSizeTest {
    @Test fun rotatedVideoReportsPortraitDisplaySizeForPip() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "pip-rotated-fixture.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("quad-rotation-90.mp4").use { input -> file.outputStream().use(input::copyTo) }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            assertEquals("90", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION))
        } finally { retriever.release() }
        val video = VideoFile(1, Uri.fromFile(file).toString(), file.name, "test", "test", 1500, file.length(), 64, 48, "video/mp4", 0, false, true)
        val controller = PlayerController(context)
        val texture = SurfaceTexture(0)
        val surface = Surface(texture)
        try {
            controller.player.setVideoSurface(surface)
            controller.load(video)
            val size = withTimeout(15_000) { controller.uiState.first { it.videoSize.width > 0 && it.videoSize.height > 0 }.videoSize }
            assertTrue("Expected portrait display dimensions: $size", size.height > size.width)
            val (width, height) = pipAspectRatio(size.width, size.height, size.pixelWidthHeightRatio)
            assertTrue(width < height)
        } finally { controller.release(); surface.release(); texture.release(); file.delete() }
    }
}
