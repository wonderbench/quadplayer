package com.quadplayer.feature.quadplayer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.MainActivity
import com.quadplayer.core.data.*
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.model.FolderInfo
import com.quadplayer.core.model.VideoFile
import com.quadplayer.core.util.DeviceCapabilityChecker
import com.quadplayer.core.util.PerformanceMonitor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuadImportInterruptionTest {
    @Test fun importFinishingAfterFocusLossAndScreenStopStaysPaused() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val file = File(context.cacheDir, "quad-interrupted-import.wav")
        val samples = ByteArray(16000 * 2 * 20)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        file.writeBytes(header.array() + samples)
        fun video(id: Long) = VideoFile(id, Uri.fromFile(file).toString(), "test$id", "test", "test", 20000, file.length(), 0, 0, "audio/wav", 0, false, true)
        val entered = CompletableDeferred<Unit>()
        val releaseImport = CompletableDeferred<Unit>()
        val videos = object : VideoRepository {
            override val videos = flowOf(emptyList<VideoFile>())
            override val favorites = flowOf(emptyList<VideoFile>())
            override val folders = flowOf(emptyList<FolderInfo>())
            override suspend fun refreshVideos() = Unit
            override suspend fun getVideo(videoId: Long): VideoFile? = null
            override suspend fun toggleFavorite(videoId: Long) = Unit
            override suspend fun importVideo(uri: Uri): VideoFile {
                if (uri.lastPathSegment == "slow") { entered.complete(Unit); releaseImport.await(); return video(2) }
                return video(1)
            }
        }
        val store = ViewModelStore()
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val other = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            val model = withContext(Dispatchers.Main) {
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = QuadPlayerViewModel(
                        context, videos, DataStoreSettingsRepository(context), QuadSessionRepository(db.quadSessionDao()),
                        DeviceCapabilityChecker(context), PerformanceMonitor(),
                    ) as T
                }
                ViewModelProvider(store, factory)[QuadPlayerViewModel::class.java].also { it.onScreenStarted(); it.addVideo(0, Uri.parse("content://test/fast")) }
            }
            withTimeout(15000) { model.uiState.first { it.slots[0].video != null && it.slots[0].playWhenReady } }
            withContext(Dispatchers.Main) { model.addVideo(1, Uri.parse("content://test/slow")) }
            withTimeout(5000) { entered.await() }
            assertTrue(manager.requestAudioFocus(other) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            withTimeout(5000) { model.uiState.first { !it.slots[0].playWhenReady } }
            withContext(Dispatchers.Main) { model.onScreenStopped() }
            releaseImport.complete(Unit)
            val paused = withTimeout(15000) { model.uiState.first { it.slots[1].video != null } }
            assertFalse(paused.slots[1].playWhenReady)
            manager.abandonAudioFocusRequest(other)
            withContext(Dispatchers.Main) { model.onScreenStarted() }
            assertFalse(withContext(Dispatchers.Main) { model.controllerAt(1)!!.player.playWhenReady })
            withContext(Dispatchers.Main) { model.toggleSlot(1) }
            assertTrue(withContext(Dispatchers.Main) { model.controllerAt(1)!!.player.playWhenReady })
        } finally {
            releaseImport.complete(Unit)
            manager.abandonAudioFocusRequest(other)
            withContext(Dispatchers.Main) { store.clear() }
            scenario.close()
            db.close()
            file.delete()
        }
    }
}
