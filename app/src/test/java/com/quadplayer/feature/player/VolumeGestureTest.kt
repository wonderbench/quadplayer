package com.quadplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class VolumeGestureTest {
    @Test fun smallMovementsAccumulateSymmetricallyAndClamp() {
        var up = 5f
        var down = 5f
        repeat(10) {
            up = accumulateVolume(up, 0.01f, 10)
            down = accumulateVolume(down, -0.01f, 10)
        }
        assertEquals(6, up.roundToInt())
        assertEquals(4, down.roundToInt())
        assertEquals(10f, accumulateVolume(9f, 1f, 10), 0f)
        assertEquals(0f, accumulateVolume(1f, -1f, 10), 0f)
    }
}
