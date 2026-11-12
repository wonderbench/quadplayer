package com.quadplayer.feature.player

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import com.quadplayer.core.model.VideoFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControllerSubtitleTest {
    @Test fun lateSubtitleProbeCannotUndoExplicitDisable() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videoFile = File(context.cacheDir, "subtitle-race-test.mp4").apply { writeText("video") }
        val first = File(context.cacheDir, "subtitle-race-test.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\nfirst\n") }
        val other = File(context.cacheDir, "subtitle-race-other.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\nother\n") }
        val video = VideoFile(1, Uri.fromFile(videoFile).toString(), videoFile.name, "test", "test", 0, videoFile.length(), 0, 0, "video/mp4", 0, false, true, videoFile.path)
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val controller = PlayerController(context, beforeSubtitleInspection = { entered.complete(Unit); resume.await() })
        try {
            controller.load(video, playWhenReady = false)
            val pending = async { controller.setExternalSubtitle(Uri.fromFile(other)) }
            withTimeout(10000) { entered.await() }
            controller.selectSubtitleTrack(null)
            resume.complete(Unit)
            pending.await()
            assertTrue(controller.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            assertEquals(Uri.fromFile(first), controller.player.currentMediaItem!!.localConfiguration!!.subtitleConfigurations.single().uri)
        } finally { resume.complete(Unit); controller.release(); videoFile.delete(); first.delete(); other.delete() }
    }

    @Test fun sidecarIsRenderedCanBeDisabledAndManuallyReenabled() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = File(context.cacheDir, "subtitle-render-test.wav")
        val samples = ByteArray(16000 * 2 * 5)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        audio.writeBytes(header.array() + samples)
        val subtitle = File(context.cacheDir, "subtitle-render-test.srt").apply { writeText("1\n00:00:00,000 --> 00:00:04,000\nvisible subtitle\n") }
        val secondAudio = File(context.cacheDir, "subtitle-render-other.wav").apply { writeBytes(audio.readBytes()) }
        val video = VideoFile(1, Uri.fromFile(audio).toString(), audio.name, "test", "test", 5000, audio.length(), 0, 0, "audio/wav", 0, false, true, audio.path)
        val controller = PlayerController(context)
        val cue = CompletableDeferred<Unit>()
        val clearedCue = CompletableDeferred<Unit>()
        val restoredCue = CompletableDeferred<Unit>()
        var waitingForClear = false
        var waitingForRestore = false
        controller.player.addListener(object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                if (cueGroup.cues.any { it.text?.toString() == "visible subtitle" }) {
                    if (waitingForRestore) restoredCue.complete(Unit) else cue.complete(Unit)
                } else if (waitingForClear && cueGroup.cues.isEmpty()) clearedCue.complete(Unit)
            }
        })
        try {
            controller.player.volume = 0f
            controller.load(video)
            withTimeout(10000) { cue.await() }
            val externalTrack = controller.player.currentTracks.groups.first { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            assertTrue(externalTrack.getTrackFormat(0).id!!.endsWith("quadplayer-external-subtitle"))
            assertTrue(controller.player.trackSelectionParameters.overrides.containsKey(externalTrack.mediaTrackGroup))
            waitingForClear = true
            controller.selectSubtitleTrack(null)
            assertTrue(controller.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            withTimeout(10000) { clearedCue.await() }
            waitingForRestore = true
            controller.setExternalSubtitle(Uri.fromFile(subtitle))
            assertFalse(controller.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            withTimeout(10000) { while (controller.player.currentTracks.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected }) delay(25) }
            withTimeout(10000) { restoredCue.await() }
            controller.selectSubtitleTrack(null)
            controller.load(video.copy(id = 2, uriString = Uri.fromFile(secondAudio).toString(), localPath = null), playWhenReady = false)
            assertFalse(controller.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        } finally { controller.release(); audio.delete(); secondAudio.delete(); subtitle.delete() }
    }

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
