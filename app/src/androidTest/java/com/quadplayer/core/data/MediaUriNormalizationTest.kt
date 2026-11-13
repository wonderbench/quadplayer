package com.quadplayer.core.data

import android.content.ContentUris
import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.provider.MediaStore
import android.view.Surface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.database.VideoEntity
import com.quadplayer.feature.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaUriNormalizationTest {
    @Test fun filesRowsMustBeVideosOnTheScannedVolume() {
        val files = Uri.parse("content://media/external_primary/file/33")
        val expected = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, 33)
        val video = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        assertEquals(expected, canonicalScannedVideoUri(files, 33, video, MediaStore.VOLUME_EXTERNAL_PRIMARY))
        assertNull(canonicalScannedVideoUri(files, 33, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, MediaStore.VOLUME_EXTERNAL_PRIMARY))
        assertNull(canonicalScannedVideoUri(files, 34, video, MediaStore.VOLUME_EXTERNAL_PRIMARY))
        assertNull(canonicalScannedVideoUri(files, 33, video, "ABCD-1234"))
        assertNull(canonicalScannedVideoUri(Uri.parse("content://other/external_primary/file/33"), 33, video, MediaStore.VOLUME_EXTERNAL_PRIMARY))
        assertEquals(expected, canonicalScannedVideoUri(expected, 33, null, MediaStore.VOLUME_EXTERNAL_PRIMARY))
    }

    // Run with -e pr48_document_uri <persisted ExternalStorageProvider document URI>.
    @Test fun persistedFilesGrantReusesScannedRowAndOpensWithoutLibraryPermission() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val value = args.getString("pr48_document_uri")
        assumeTrue("Requires a persisted test document grant", !value.isNullOrBlank())
        val document = Uri.parse(value)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scanner = MediaStoreVideoScanner(context)
        val mapped = MediaStore.getMediaUri(context, document)
        assertNotNull(mapped)
        assertEquals("file", mapped!!.pathSegments[1])
        val canonical = scanner.persistedMediaUri(document)
        assertNotNull(canonical)
        if (args.getString("pr48_expect_restricted") == "true") {
            assertFalse(runCatching { context.contentResolver.openFileDescriptor(canonical!!, "r")?.use { true } ?: false }.getOrDefault(false))
        }
        val database = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        try {
            val dao = database.videoDao()
            dao.upsert(VideoEntity(id = 42, uriString = canonical.toString(), displayName = "original", folderPath = "Download", folderName = "Download", durationMs = 100, sizeBytes = 1, width = 1, height = 1, mimeType = "video/mp4", dateModified = 0, isFavorite = true))
            val repository = DefaultVideoRepository(dao, scanner, database)
            val imported = repository.importVideo(document)!!
            assertEquals(42L, imported.id)
            assertTrue(imported.isFavorite)
            assertFalse(imported.isManuallyAdded)
            assertEquals(1, dao.getAll().size)
            val fromLibrary = repository.getVideo(42)!!
            val fromSavedSession = repository.importVideo(canonical!!)!!
            assertEquals(42L, fromSavedSession.id)
            assertEquals(1, dao.getAll().size)
            for (video in listOf(imported, fromLibrary, fromSavedSession)) {
                assertEquals(canonical.toString(), video.uriString)
                assertEquals(document, video.uri)
                assertTrue(context.contentResolver.openFileDescriptor(video.uri, "r")?.use { true } == true)
            }
            withContext(Dispatchers.Main) {
                val controller = PlayerController(context)
                val texture = SurfaceTexture(0)
                val surface = Surface(texture)
                try {
                    controller.player.setVideoSurface(surface)
                    assertTrue(controller.load(fromSavedSession))
                    withTimeout(15_000) { controller.uiState.first { it.isPlaying && it.videoSize.width > 0 } }
                } finally { controller.release(); surface.release(); texture.release() }
            }
        } finally { database.close() }
        Unit
    }
}
