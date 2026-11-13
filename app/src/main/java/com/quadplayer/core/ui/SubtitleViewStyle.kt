package com.quadplayer.core.ui

import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.quadplayer.core.model.SubtitleStyle

@UnstableApi
internal fun PlayerView.applySubtitleStyle(style: SubtitleStyle) {
    subtitleView?.apply {
        val useEmbedded = style == SubtitleStyle()
        setApplyEmbeddedStyles(useEmbedded)
        setApplyEmbeddedFontSizes(useEmbedded)
        setStyle(CaptionStyleCompat(style.foregroundColor.toInt(), style.backgroundColor.toInt(), Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null))
        val scale = style.fontScale.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
        setFractionalTextSize(0.0533f * scale)
    }
}
