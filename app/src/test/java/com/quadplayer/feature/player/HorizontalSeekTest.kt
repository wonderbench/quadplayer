package com.quadplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class HorizontalSeekTest {
    @Test fun horizontalSeekIsBoundedAndProportional() {
        assertEquals(60_000L, horizontalSeekTarget(0, 240_000, 500f, 1000f))
        assertEquals(0L, horizontalSeekTarget(1000, 240_000, -1000f, 1000f))
        assertEquals(240_000L, horizontalSeekTarget(230_000, 240_000, 1000f, 1000f))
        assertEquals(0L, horizontalSeekTarget(0, 0, 100f, 1000f))
    }
}
