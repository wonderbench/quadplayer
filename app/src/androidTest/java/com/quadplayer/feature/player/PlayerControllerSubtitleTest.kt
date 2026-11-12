package com.quadplayer.feature.player

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.model.VideoFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControllerSubtitleTest {
    @Test fun localSiblingLoadsAndExplicitDisableStaysDisabledOnReload() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videoFile = File(context.cacheDir, "subtitle-auto-test.mp4").apply { writeText("video") }
        val subtitle = File(context.cacheDir, "subtitle-auto-test.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\ntext\n") }
        val video = VideoFile(1, Uri.fromFile(videoFile).toString(), videoFile.name, "test", "test", 0, videoFile.length(), 0, 0, "video/mp4", 0, false, true, videoFile.path)
        val controller = PlayerController(context)
        try {
            controller.load(video, playWhenReady = false)
            assertEquals(Uri.fromFile(subtitle), controller.player.currentMediaItem!!.localConfiguration!!.subtitleConfigurations.single().uri)
            controller.setExternalSubtitle(null)
            assertTrueNoSubtitles(controller)
            assertFalse(controller.player.playWhenReady)
            controller.load(video, playWhenReady = false)
            assertTrueNoSubtitles(controller)
        } finally { controller.release(); videoFile.delete(); subtitle.delete() }
    }

    private fun assertTrueNoSubtitles(controller: PlayerController) {
        assertEquals(0, controller.player.currentMediaItem!!.localConfiguration!!.subtitleConfigurations.size)
    }
}
