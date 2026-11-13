package com.quadplayer.feature.player

import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.ceil

/** Space occupied by bottom controls in the subtitle view's actual bounds. */
internal fun subtitleControlsInset(subtitleTop: Float, subtitleHeight: Int, controlsTop: Float?): Int {
    if (subtitleHeight <= 0 || !subtitleTop.isFinite() || controlsTop == null || !controlsTop.isFinite()) return 0
    return ceil((subtitleTop + subtitleHeight - controlsTop).coerceIn(0f, subtitleHeight.toFloat())).toInt()
}

/** Keeps positioned and unpositioned cues inside the unobscured part of the video. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class SubtitleControlsInset(private val playerView: PlayerView) {
    private val subtitle = playerView.subtitleView
    private val initialLeft = subtitle?.paddingLeft ?: 0
    private val initialTop = subtitle?.paddingTop ?: 0
    private val initialRight = subtitle?.paddingRight ?: 0
    private val initialBottom = subtitle?.paddingBottom ?: 0
    private var playerTop = Float.NaN
    private var controlsTop: Float? = null
    private var fontScale = 1f
    private var lastTextSizePx = Float.NaN
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }

    init {
        subtitle?.addOnLayoutChangeListener(layoutListener)
        (subtitle?.parent as? View)?.addOnLayoutChangeListener(layoutListener)
    }

    fun update(playerTop: Float, controlsTop: Float?, fontScale: Float = 1f) {
        this.playerTop = playerTop
        this.controlsTop = controlsTop
        this.fontScale = fontScale.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
        // applySubtitleStyle can reset the size during a Compose update.
        lastTextSizePx = Float.NaN
        apply()
    }

    private fun apply() {
        val view = subtitle ?: return
        val bounds = Rect(0, 0, view.width, view.height)
        playerView.offsetDescendantRectToMyCoords(view, bounds)
        val extra = subtitleControlsInset(playerTop + bounds.top, view.height, controlsTop)
        val bottom = (initialBottom + extra).coerceAtMost((view.height - initialTop).coerceAtLeast(0))
        if (view.paddingBottom != bottom) view.setPadding(initialLeft, initialTop, initialRight, bottom)
        if (view.height > initialTop + initialBottom) {
            val textSize = (view.height - initialTop - initialBottom) * 0.0533f * fontScale
            if (textSize != lastTextSizePx) {
                view.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                lastTextSizePx = textSize
            }
        }
    }
}
