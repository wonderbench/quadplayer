package com.quadplayer.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quadplayer.MainActivity
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalOpenLifecycleTest {
    @Test fun repeatedExternalOpensReleasePreviousScreensWithBothBackgroundPolicies() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, BackgroundPlaybackTestEntryPoint::class.java)
        val host = entry.host()
        val settings = entry.settings()
        val original = settings.settings.first().backgroundPlayback
        val files = (1..2).map { File(context.cacheDir, "external-open-$it.mp4").apply { writeBytes(ByteArray(64)) } }
        try {
            for (background in listOf(false, true)) {
                settings.setBackgroundPlayback(background)
                val scenario = ActivityScenario.launch(MainActivity::class.java)
                lateinit var launchIntent: Intent
                scenario.onActivity { launchIntent = Intent(it.intent) }
                try {
                    repeat(6) { index ->
                        val uri = Uri.fromFile(files[index % 2])
                        scenario.onActivity { activity ->
                            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4"))
                        }
                        withTimeout(15_000) {
                            while (withContext(Dispatchers.Main) {
                                    host.currentVideoUri() != uri.toString() || host.screenSessionCount() != 1 || host.retainedSessionCount() != 0
                                }) delay(50)
                        }
                        assertEquals(1, withContext(Dispatchers.Main) { host.screenSessionCount() })
                    }
                } finally {
                    // ActivityScenario matches lifecycle callbacks against the launch intent.
                    scenario.onActivity { it.setIntent(launchIntent) }
                    scenario.close()
                }
                withTimeout(10_000) { while (withContext(Dispatchers.Main) { host.screenSessionCount() != 0 }) delay(50) }
            }
        } finally {
            settings.setBackgroundPlayback(original)
            withContext(Dispatchers.Main) { host.stopService() }
            files.forEach { it.delete() }
        }
    }
}
