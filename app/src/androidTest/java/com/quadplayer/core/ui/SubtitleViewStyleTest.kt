package com.quadplayer.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quadplayer.core.model.SubtitleStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class SubtitleViewStyleTest {
    @Test fun customColorIsRenderedOnSubtitleView() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = PlayerView(ApplicationProvider.getApplicationContext<Context>())
            view.applySubtitleStyle(SubtitleStyle(foregroundColor = 0xFFFFFF00.toInt().toLong(), backgroundColor = 0L))
            val subtitles = requireNotNull(view.subtitleView)
            subtitles.setCues(listOf(Cue.Builder().setText("Caption").build()))
            subtitles.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            subtitles.layout(0, 0, 800, 400)
            val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
            subtitles.draw(Canvas(bitmap))
            val pixels = IntArray(800 * 400)
            bitmap.getPixels(pixels, 0, 800, 0, 0, 800, 400)
            assertTrue(pixels.any { Color.alpha(it) > 200 && Color.red(it) > 200 && Color.green(it) > 200 && Color.blue(it) < 80 })
            bitmap.recycle()
        }
    }
}
