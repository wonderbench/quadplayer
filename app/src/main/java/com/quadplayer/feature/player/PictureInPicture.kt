package com.quadplayer.feature.player

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational

internal fun pipAspectRatio(width: Int, height: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 16 to 9
    return when {
        width.toLong() * 100 > height.toLong() * 239 -> 239 to 100
        height.toLong() * 100 > width.toLong() * 239 -> 100 to 239
        else -> width to height
    }
}

internal fun pipParams(width: Int, height: Int, autoEnter: Boolean): PictureInPictureParams {
    val (w, h) = pipAspectRatio(width, height)
    return PictureInPictureParams.Builder().setAspectRatio(Rational(w, h)).apply {
        if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(autoEnter)
    }.build()
}
