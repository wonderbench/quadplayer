package com.quadplayer.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.model.LayoutMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuadPresetRepositoryTest {
    @Test fun savesRestoresRejectsDuplicatesAndDeletesWithoutTouchingVideoFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "presets-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = QuadPresetRepository.forTesting(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val slot = SavedQuadSlot("content://example/video/1", 1234, 1.25f, true)
        val snapshot = SavedQuadSession(listOf(slot, null, null, null), LayoutMode.FOCUS_GRID, 0, 0)
        try {
            assertEquals(SavePresetResult.INVALID_NAME, repository.save(" ", snapshot))
            assertEquals(SavePresetResult.EMPTY_SESSION, repository.save("empty", snapshot.copy(slots = List(4) { null })))
            assertEquals(SavePresetResult.SAVED, repository.save("  Test  ", snapshot))
            assertEquals(SavePresetResult.DUPLICATE_NAME, repository.save("test", snapshot))
            val saved = repository.presets.first().single()
            assertEquals("Test", saved.name)
            assertEquals(snapshot, repository.read(saved.id))
            repository.delete(saved.id)
            assertNull(repository.read(saved.id))
            assertEquals(emptyList<QuadPreset>(), repository.presets.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
