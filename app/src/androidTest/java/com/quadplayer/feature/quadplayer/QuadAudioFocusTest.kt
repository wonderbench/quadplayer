package com.quadplayer.feature.quadplayer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuadAudioFocusTest {
    @Test fun transientFocusLossInterruptsGridAndFocusCanBeReacquired() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val interrupted = CompletableDeferred<Unit>()
        val focus = QuadAudioFocus(context) { interrupted.complete(Unit) }
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val other = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            withContext(Dispatchers.Main) {
                focus.start()
                assertTrue(focus.request())
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(other))
            }
            withTimeout(5000) { interrupted.await() }
            withContext(Dispatchers.Main) {
                manager.abandonAudioFocusRequest(other)
                assertTrue(focus.request())
                focus.stop()
                focus.start()
                assertTrue(focus.request())
            }
        } finally {
            withContext(Dispatchers.Main) { manager.abandonAudioFocusRequest(other); focus.stop() }
            scenario.close()
        }
    }
}
