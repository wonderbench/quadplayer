package com.quadplayer.feature.player

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import kotlin.math.roundToInt

internal fun pipAspectRatio(width: Int, height: Int, pixelRatio: Float = 1f): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 16 to 9
    val pixelAspect = pixelRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val ratio = width.toDouble() * pixelAspect / height
    return when {
        ratio > 2.39 -> 239 to 100
        ratio < 100.0 / 239.0 -> 100 to 239
        pixelAspect == 1f -> width to height
        else -> (ratio * 100_000).roundToInt() to 100_000
    }
}

internal fun pipParams(width: Int, height: Int, autoEnter: Boolean, pixelRatio: Float = 1f): PictureInPictureParams {
    val (w, h) = pipAspectRatio(width, height, pixelRatio)
    return PictureInPictureParams.Builder().setAspectRatio(Rational(w, h)).apply {
        if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(autoEnter)
    }.build()
}
