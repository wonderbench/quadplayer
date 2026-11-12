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
    }
}
