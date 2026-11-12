package com.quadplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureInPictureTest {
    @Test fun handlesMissingDimensionsAndExtremeRatios() {
        assertEquals(16 to 9, pipAspectRatio(0, 0))
        assertEquals(1920 to 1080, pipAspectRatio(1920, 1080))
        assertEquals(239 to 100, pipAspectRatio(4000, 1000))
        assertEquals(100 to 239, pipAspectRatio(1000, 4000))
        assertEquals(239 to 100, pipAspectRatio(Int.MAX_VALUE, 1))
        val (width, height) = pipAspectRatio(720, 576, 16f / 15f)
        org.junit.Assert.assertEquals(4.0 / 3.0, width.toDouble() / height, 0.00001)
        assertEquals(320 to 180, pipAspectRatio(320, 180, Float.NaN))
    }
}
