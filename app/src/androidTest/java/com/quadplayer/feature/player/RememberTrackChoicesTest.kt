package com.quadplayer.feature.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.model.VideoFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
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
class RememberTrackChoicesTest {
    @Test fun remembersSelectedAudioTrackWhenReopeningMergedSources() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = UUID.randomUUID().toString()
        val original = File(context.cacheDir, "$name.wav")
        val external = File(context.cacheDir, "$name-other.wav")
        val samples = ByteArray(16000 * 2 * 3)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        original.writeBytes(header.array() + samples)
        external.writeBytes(original.readBytes())
        val video = VideoFile(1, Uri.fromFile(original).toString(), original.name, "test", "test", 3000, original.length(), 0, 0, "audio/wav", 0, false, true, null)
        val first = PlayerController(context, rememberTrackChoices = true)
        var selectedIndex = -1
        try {
            first.load(video, playWhenReady = false)
            first.setExternalAudio(Uri.fromFile(external))
            withTimeout(10000) { while (first.uiState.value.availableAudioTracks.size < 2) delay(25) }
            selectedIndex = first.uiState.value.availableAudioTracks.indexOfFirst { !it.selected }
            assertTrue(selectedIndex >= 0)
            val option = first.uiState.value.availableAudioTracks[selectedIndex]
            first.selectAudioTrack(option)
            withTimeout(10000) { while (!first.uiState.value.availableAudioTracks[selectedIndex].selected) delay(25) }
        } finally { first.release() }
        val restored = PlayerController(context, rememberTrackChoices = true)
        try {
            restored.load(video, playWhenReady = false)
            withTimeout(10000) { while (restored.uiState.value.availableAudioTracks.size < 2) delay(25) }
            withTimeout(10000) { while (!restored.uiState.value.availableAudioTracks[selectedIndex].selected) delay(25) }
        } finally { restored.release(); original.delete(); external.delete() }
    }

    @Test fun remembersExternalSubtitleAndDisabledTracksPerVideoAcrossPlayers() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = UUID.randomUUID().toString()
        val audio = File(context.cacheDir, "$name.wav")
        val second = File(context.cacheDir, "$name-other.wav")
        val subtitle = File(context.cacheDir, "$name-external.srt").apply {
            writeText("1\n00:00:00,000 --> 00:00:04,000\nremembered subtitle\n")
        }
        val samples = ByteArray(16000 * 2 * 5)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        audio.writeBytes(header.array() + samples)
        second.writeBytes(audio.readBytes())
        fun video(file: File) = VideoFile(1, Uri.fromFile(file).toString(), file.name, "test", "test", 5000, file.length(), 0, 0, "audio/wav", 0, false, true, null)
        val first = PlayerController(context, rememberTrackChoices = true)
        try {
            first.load(video(audio), playWhenReady = false)
            first.setExternalSubtitle(Uri.fromFile(subtitle))
            first.selectAudioTrack(null)
        } finally { first.release() }
        val secondPlayer = PlayerController(context, rememberTrackChoices = true)
        val cue = CompletableDeferred<Unit>()
        secondPlayer.player.addListener(object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                if (cueGroup.cues.any { it.text?.toString() == "remembered subtitle" }) cue.complete(Unit)
            }
        })
        try {
            secondPlayer.load(video(audio), playWhenReady = false)
            assertTrue(secondPlayer.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
            assertEquals(Uri.fromFile(subtitle), secondPlayer.player.currentMediaItem!!.localConfiguration!!.subtitleConfigurations.single().uri)
            secondPlayer.play()
            withTimeout(10000) { cue.await() }
            secondPlayer.selectSubtitleTrack(null)
            secondPlayer.load(video(second), playWhenReady = false)
            assertFalse(secondPlayer.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
            assertFalse(secondPlayer.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            assertTrue(secondPlayer.player.currentMediaItem!!.localConfiguration!!.subtitleConfigurations.isEmpty())
            secondPlayer.load(video(audio), playWhenReady = false)
            assertTrue(secondPlayer.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
            assertTrue(secondPlayer.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            secondPlayer.setExternalAudio(Uri.fromFile(second))
            assertFalse(secondPlayer.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
        } finally { secondPlayer.release(); audio.delete(); second.delete(); subtitle.delete() }
    }

    @Test fun missingExternalSubtitleFallsBackWithoutBreakingPlayback() = runBlocking(Dispatchers.Main) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = UUID.randomUUID().toString()
        val audio = File(context.cacheDir, "$name.wav")
        val subtitle = File(context.cacheDir, "$name-external.srt").apply { writeText("1\n00:00:00,000 --> 00:00:01,000\ntext\n") }
        val samples = ByteArray(16000 * 2)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        audio.writeBytes(header.array() + samples)
        val video = VideoFile(1, Uri.fromFile(audio).toString(), audio.name, "test", "test", 1000, audio.length(), 0, 0, "audio/wav", 0, false, true, null)
        val first = PlayerController(context, rememberTrackChoices = true)
        try { first.load(video, playWhenReady = false); first.setExternalSubtitle(Uri.fromFile(subtitle)) } finally { first.release() }
        subtitle.delete()
        val restored = PlayerController(context, rememberTrackChoices = true)
        try {
            restored.load(video, playWhenReady = false)
            withTimeout(10000) { while (restored.player.playbackState != Player.STATE_READY) delay(25) }
            assertTrue(restored.player.currentMediaItem!!.localConfiguration!!.subtitleConfigurations.isEmpty())
        } finally { restored.release(); audio.delete() }
    }
}
