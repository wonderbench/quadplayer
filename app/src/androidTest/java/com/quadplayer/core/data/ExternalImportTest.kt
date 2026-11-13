package com.quadplayer.core.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.database.VideoEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalImportTest {
    @Test fun reauthorizationRestoresExistingRecordButUnreadableUriIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "external-import-test.mp4").apply { writeBytes(ByteArray(64)) }
        val uri = Uri.fromFile(file)
        val database = Room.inMemoryDatabaseBuilder(context, QuadPlayerDatabase::class.java).build()
        try {
            val dao = database.videoDao()
            val original = VideoEntity(id = 42, uriString = uri.toString(), displayName = "test", folderPath = "test", folderName = "test", durationMs = 100, sizeBytes = 64, width = 0, height = 0, mimeType = "video/mp4", dateModified = 0, isFavorite = true, isAccessible = false)
            dao.upsert(original)
            val repository = DefaultVideoRepository(dao, MediaStoreVideoScanner(context), database)
            assertEquals(42L, repository.importVideo(uri)?.id)
            assertTrue(dao.getById(42)!!.isAccessible)
            assertTrue(dao.getById(42)!!.isFavorite)
            file.delete()
            assertNull(repository.importVideo(uri))
        } finally {
            database.close()
            file.delete()
        }
    }
}
