package com.quadplayer.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SubtitleControlsRenderTest {
    @Test fun defaultAndExplicitCuesAvoidControlsAndReturnWhenHidden() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val player = PlayerView(context)
            val subtitle = requireNotNull(player.subtitleView)
            subtitle.setStyle(CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.TRANSPARENT, null))
            val inset = SubtitleControlsInset(player)
            fun layout() {
                player.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
                player.layout(0, 0, 800, 400)
            }
            fun boundsOf(cue: Cue): Rect {
                subtitle.setCues(listOf(cue))
                layout()
                val bitmap = Bitmap.createBitmap(subtitle.width, subtitle.height, Bitmap.Config.ARGB_8888)
                subtitle.draw(Canvas(bitmap))
                var bottom = -1
                var top = bitmap.height
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    if (Color.alpha(bitmap.getPixel(x, y)) > 0) { bottom = y; top = minOf(top, y) }
                }
                bitmap.recycle()
                assertTrue("Expected subtitle pixels", bottom >= 0)
                val bounds = Rect(0, 0, subtitle.width, subtitle.height)
                player.offsetDescendantRectToMyCoords(subtitle, bounds)
                return Rect(0, bounds.top + top, subtitle.width, bounds.top + bottom + 1)
            }
            val ordinary = Cue.Builder().setText("Default cue").build()
            val positioned = Cue.Builder().setText("Explicit cue")
                .setLine(0.92f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_END).build()
            layout()
            inset.update(0f, 290f)
            val raisedDefault = boundsOf(ordinary)
            assertTrue("Default cue overlaps controls", raisedDefault.bottom <= 290)
            assertTrue("Positioned cue overlaps controls", boundsOf(positioned).bottom <= 290)
            val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val imageCue = Cue.Builder().setBitmap(image).setPosition(0.5f).setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                .setLine(0.92f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_END).setSize(0.2f).setBitmapHeight(0.15f).build()
            assertTrue("Positioned bitmap overlaps controls", boundsOf(imageCue).bottom <= 290)
            image.recycle()
            inset.update(0f, null)
            assertTrue("Positioned cue did not return to original region", boundsOf(positioned).bottom > 290)
            val defaultWithoutControls = boundsOf(ordinary)
            assertTrue("Default font size changed when controls opened", kotlin.math.abs(defaultWithoutControls.height() - raisedDefault.height()) <= 1)

            // PlayerView's video frame may be letterboxed inside the view.
            player.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame).setAspectRatio(8f / 3f)
            layout()
            inset.update(0f, 290f)
            assertTrue("Letterboxed cue overlaps controls", boundsOf(positioned).bottom <= 290)
            inset.update(100f, 390f)
            assertTrue("Offset player cue overlaps controls", boundsOf(positioned).bottom <= 290)
        }
    }
}
