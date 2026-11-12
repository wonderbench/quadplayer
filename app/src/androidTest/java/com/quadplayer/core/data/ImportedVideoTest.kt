package com.quadplayer.core.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportedVideoTest {
    @Test fun damagedReadableFileFallsBackAndMissingFileIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "damaged-import.mp4")
        val missing = File(context.cacheDir, "missing-import.mp4")
        file.writeText("not a media file")
        try {
            val scanner = MediaStoreVideoScanner(context)
            val imported = scanner.inspectImportedVideo(Uri.fromFile(file))
            assertNotNull(imported)
            assertEquals(0L, imported!!.durationMs)
            assertEquals(0, imported.width)
            assertNull(scanner.inspectImportedVideo(Uri.fromFile(missing)))
        } finally { file.delete() }
    }
}
