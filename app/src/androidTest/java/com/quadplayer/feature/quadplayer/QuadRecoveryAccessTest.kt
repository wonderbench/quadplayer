package com.quadplayer.feature.quadplayer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuadRecoveryAccessTest {
    @Test fun readableMediaIsDistinguishedFromMissingMedia() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "restore-access-test")
        file.writeText("fixture")
        try {
            assertTrue(canReadMedia(context, Uri.fromFile(file)))
            assertFalse(canReadMedia(context, Uri.fromFile(File(context.cacheDir, "missing-restore-access-test"))))
        } finally { file.delete() }
    }
}
