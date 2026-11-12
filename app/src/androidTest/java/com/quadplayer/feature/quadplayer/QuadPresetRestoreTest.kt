package com.quadplayer.feature.quadplayer

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.data.*
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.model.FolderInfo
import com.quadplayer.core.model.LayoutMode
import com.quadplayer.core.model.VideoFile
import com.quadplayer.core.util.DeviceCapabilityChecker
import com.quadplayer.core.util.PerformanceMonitor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuadPresetRestoreTest {
    @Test fun failedRecoveryKeepsTheSavedSessionForLater() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val storeFile = File(context.cacheDir, "recover-${UUID.randomUUID()}.preferences_pb")
        val presets = QuadPresetRepository.forTesting(PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile }))
        val sessions = QuadSessionRepository(db.quadSessionDao())
        val missing = SavedQuadSession(listOf(SavedQuadSlot("file:///missing/quad-recovery-video", 1000, 1f, true), null, null, null), LayoutMode.EQUAL_GRID, 0, 0)
        sessions.save(missing)
        assertEquals(missing, sessions.read())
        val videos = object : VideoRepository {
            override val videos = flowOf(emptyList<VideoFile>())
            override val favorites = flowOf(emptyList<VideoFile>())
            override val folders = flowOf(emptyList<FolderInfo>())
            override suspend fun refreshVideos() = Unit
            override suspend fun getVideo(videoId: Long): VideoFile? = null
            override suspend fun toggleFavorite(videoId: Long) = Unit
            override suspend fun importVideo(uri: Uri): VideoFile? = null
        }
        val store = ViewModelStore()
        try {
            val model = withContext(Dispatchers.Main) {
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = QuadPlayerViewModel(
                        context, videos, DataStoreSettingsRepository(context), sessions, presets,
                        DeviceCapabilityChecker(context), PerformanceMonitor(),
                    ) as T
                }
                ViewModelProvider(store, factory)[QuadPlayerViewModel::class.java]
            }
            withTimeout(15000) { model.uiState.first { it.offerRecovery } }
            withContext(Dispatchers.Main) { model.restoreSession() }
            withTimeout(15000) { model.uiState.first { it.recoveryAvailable && !it.restoring } }
            withContext(Dispatchers.Main) { model.saveSession() }
            assertEquals(missing, sessions.read())
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            db.close(); scope.cancel(); storeFile.delete()
        }
    }

    @Test fun restoreCancelsPendingImportAndMissingPresetPreservesCurrentQuad() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val storeFile = File(context.cacheDir, "restore-${UUID.randomUUID()}.preferences_pb")
        val presets = QuadPresetRepository.forTesting(PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile }))
        val files = (1..3).map { index -> File(context.cacheDir, "quad-restore-${UUID.randomUUID()}-$index.wav") }
        val samples = ByteArray(16000 * 2 * 4)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size)
        files.forEach { it.writeBytes(header.array() + samples) }
        fun video(index: Int) = VideoFile(index.toLong(), Uri.fromFile(files[index - 1]).toString(), "test$index", "test", "test", 4000, files[index - 1].length(), 0, 0, "audio/wav", 0, false, true)
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val videos = object : VideoRepository {
            override val videos = flowOf(emptyList<VideoFile>())
            override val favorites = flowOf(emptyList<VideoFile>())
            override val folders = flowOf(emptyList<FolderInfo>())
            override suspend fun refreshVideos() = Unit
            override suspend fun getVideo(videoId: Long): VideoFile? = null
            override suspend fun toggleFavorite(videoId: Long) = Unit
            override suspend fun importVideo(uri: Uri): VideoFile? {
                val index = files.indexOfFirst { Uri.fromFile(it) == uri } + 1
                if (index == 3) { entered.complete(Unit); resume.await() }
                return if (index in 1..3) video(index) else null
            }
        }
        val snapshot = SavedQuadSession(listOf(SavedQuadSlot(video(2).uriString, 1000, 1f, false), null, null, null), LayoutMode.EQUAL_GRID, 0, 0)
        val missing = snapshot.copy(slots = listOf(SavedQuadSlot("file:///missing/quad-preset-video", 0, 1f, false), null, null, null))
        assertEquals(SavePresetResult.SAVED, presets.save("valid", snapshot))
        assertEquals(SavePresetResult.SAVED, presets.save("missing", missing))
        val saved = presets.presets.first().associateBy { it.name }
        val store = ViewModelStore()
        try {
            val model = withContext(Dispatchers.Main) {
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = QuadPlayerViewModel(
                        context, videos, DataStoreSettingsRepository(context), QuadSessionRepository(db.quadSessionDao()), presets,
                        DeviceCapabilityChecker(context), PerformanceMonitor(),
                    ) as T
                }
                ViewModelProvider(store, factory)[QuadPlayerViewModel::class.java].also { it.onScreenStarted(); it.addVideo(0, Uri.fromFile(files[0])) }
            }
            withTimeout(15000) { model.uiState.first { it.presetsReady && it.slots[0].video?.id == 1L } }
            withContext(Dispatchers.Main) { model.addVideo(1, Uri.fromFile(files[2])) }
            withTimeout(5000) { entered.await() }
            withContext(Dispatchers.Main) { model.restorePreset(saved.getValue("valid").id); resume.complete(Unit) }
            val restored = withTimeout(15000) { model.uiState.first { !it.restoring && it.slots[0].video?.id == 2L } }
            assertEquals(null, restored.slots[1].video)
            assertFalse(restored.slots[0].playWhenReady)
            assertTrue(restored.slots[0].isAudioSource)

            withContext(Dispatchers.Main) { model.restorePreset(saved.getValue("missing").id) }
            val preserved = withTimeout(15000) { model.uiState.first { !it.restoring && it.warningMessage?.resourceId == com.quadplayer.R.string.quad_session_some_missing } }
            assertEquals(2L, preserved.slots[0].video?.id)
        } finally {
            resume.complete(Unit)
            withContext(Dispatchers.Main) { store.clear() }
            db.close()
            scope.cancel()
            storeFile.delete()
            files.forEach { it.delete() }
        }
    }
}
