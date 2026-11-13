package com.quadplayer.feature.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.MainActivity
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.database.VideoEntity
import com.quadplayer.core.database.PlaylistEntity
import com.quadplayer.core.database.PlaylistVideoEntity
import com.quadplayer.core.model.DecoderMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class BackgroundPlaybackTest {
    @Test fun controllerSurvivesScreenAndStopReleasesSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, BackgroundPlaybackTestEntryPoint::class.java)
        val settings = entry.settings()
        val oldBackground = settings.settings.first().backgroundPlayback
        val oldDecoder = settings.settings.first().decoderMode
        settings.setBackgroundPlayback(true)
        val file = File(context.cacheDir, "background-session-test.wav")
        val samples = ByteArray(16000 * 2 * 20)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        file.writeBytes(header.array() + samples)
        val id = entry.database().videoDao().upsert(VideoEntity(
            uriString = Uri.fromFile(file).toString(), displayName = "background-test", folderPath = "test",
            folderName = "test", durationMs = 20000, sizeBytes = file.length(), width = 0, height = 0,
            mimeType = "audio/wav", dateModified = 0, isManuallyAdded = true,
        ))
        val secondFile = File(context.cacheDir, "background-session-test-2.wav")
        file.copyTo(secondFile, overwrite = true)
        val secondId = entry.database().videoDao().upsert(VideoEntity(
            uriString = Uri.fromFile(secondFile).toString(), displayName = "background-test-2", folderPath = "test",
            folderName = "test", durationMs = 20000, sizeBytes = secondFile.length(), width = 0, height = 0,
            mimeType = "audio/wav", dateModified = 0, isManuallyAdded = true,
        ))
        val playlistId = entry.database().playlistDao().insertPlaylist(PlaylistEntity(name = "background-test", createdAt = 0, updatedAt = 0))
        entry.database().playlistDao().upsertEntries(listOf(PlaylistVideoEntity(playlistId, id, 0), PlaylistVideoEntity(playlistId, secondId, 1)))
        val host = entry.host()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var mediaController: MediaController? = null
        try {
            val playback = withContext(Dispatchers.Main) {
                host.createScreenSession(SavedStateHandle(mapOf("videoId" to id, "playlistId" to playlistId))).also {
                    host.setScreenVisible(it, true)
                    host.startService()
                }
            }
            withTimeout(15000) { playback.state.first { it.video?.id == id && it.playerState.isPlaying } }
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = withContext(Dispatchers.Main) { MediaController.Builder(context, token).buildAsync() }
            val controller = withContext(Dispatchers.IO) { future.get(15, TimeUnit.SECONDS) }
            mediaController = controller
            assertFalse(withContext(Dispatchers.Main) { controller.availableCommands.contains(Player.COMMAND_SET_MEDIA_ITEM) })
            assertFalse(withContext(Dispatchers.Main) { controller.availableCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS) })
            withContext(Dispatchers.Main) {
                host.setScreenVisible(playback, false)
                host.releaseScreen(playback)
            }
            val before = withContext(Dispatchers.Main) { controller.currentPosition }
            delay(800)
            val after = withContext(Dispatchers.Main) { controller.currentPosition }
            assertTrue(after > before)
            withContext(Dispatchers.Main) { controller.pause() }
            withTimeout(5000) { while (withContext(Dispatchers.Main) { controller.playWhenReady }) delay(50) }
            withContext(Dispatchers.Main) { controller.play() }
            withTimeout(5000) { while (!withContext(Dispatchers.Main) { controller.isPlaying }) delay(50) }
            val oldPlayer = withContext(Dispatchers.Main) { playback.controller.player }
            settings.setDecoderMode(if (oldDecoder == DecoderMode.SOFTWARE) DecoderMode.HARDWARE else DecoderMode.SOFTWARE)
            withTimeout(10000) { while (withContext(Dispatchers.Main) { playback.controller.player === oldPlayer }) delay(50) }
            withTimeout(10000) { while (!withContext(Dispatchers.Main) { controller.isPlaying }) delay(50) }
            withContext(Dispatchers.Main) { controller.pause() }
            withTimeout(5000) { while (withContext(Dispatchers.Main) { controller.playWhenReady }) delay(50) }
            withContext(Dispatchers.Main) { controller.play() }
            withTimeout(5000) { while (!withContext(Dispatchers.Main) { controller.isPlaying }) delay(50) }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener { }.build()
            assertTrue(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            try {
                withTimeout(5000) { while (withContext(Dispatchers.Main) { controller.isPlaying }) delay(50) }
            } finally { audioManager.abandonAudioFocusRequest(focus) }
            withTimeout(5000) { while (!withContext(Dispatchers.Main) { controller.isPlaying }) delay(50) }
            withContext(Dispatchers.Main) { controller.seekTo(19999) }
            withTimeout(15000) { playback.state.first { it.video?.id == secondId } }
            withContext(Dispatchers.Main) { controller.stop() }
            withTimeout(10000) { while (withContext(Dispatchers.Main) { host.hasActiveSession() }) delay(50) }
            withContext(Dispatchers.Main) { controller.release() }
            mediaController = null
            delay(500)
            val second = withContext(Dispatchers.Main) {
                host.createScreenSession(SavedStateHandle(mapOf("videoId" to id, "playlistId" to -1L))).also {
                    host.setScreenVisible(it, true)
                    host.startService()
                }
            }
            withTimeout(15000) { second.state.first { it.video?.id == id && it.playerState.isPlaying } }
            val secondFuture = withContext(Dispatchers.Main) { MediaController.Builder(context, token).buildAsync() }
            val boundController = withContext(Dispatchers.IO) { secondFuture.get(15, TimeUnit.SECONDS) }
            mediaController = boundController
            // Disable while visible, then leave with the controller still bound.
            settings.setBackgroundPlayback(false)
            withTimeout(5000) { while (withContext(Dispatchers.Main) { second.backgroundEnabled }) delay(50) }
            withContext(Dispatchers.Main) {
                host.setScreenVisible(second, false)
                second.onBackgroundStateChanged(false, false)
                host.stopService()
                host.releaseScreen(second)
            }
            withTimeout(10000) { while (withContext(Dispatchers.Main) { boundController.isConnected }) delay(50) }
            withContext(Dispatchers.Main) { boundController.play() }
            withTimeout(10000) { while (withContext(Dispatchers.Main) { host.hasActiveSession() }) delay(50) }
        } finally {
            withContext(Dispatchers.Main) { mediaController?.release(); host.stopService() }
            scenario.close()
            settings.setBackgroundPlayback(oldBackground)
            settings.setDecoderMode(oldDecoder)
            withContext(Dispatchers.IO) {
                entry.database().playlistDao().deletePlaylistById(playlistId)
                entry.database().openHelper.writableDatabase.execSQL("DELETE FROM videos WHERE id IN (?, ?)", arrayOf(id, secondId))
            }
            file.delete()
            secondFile.delete()
        }
    }
}
