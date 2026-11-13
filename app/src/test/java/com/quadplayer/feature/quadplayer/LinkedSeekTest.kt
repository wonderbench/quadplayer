package com.quadplayer.feature.quadplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkedSeekTest {
    @Test fun movesEveryVideoBySameDelta() {
        val result = linkedSeekTargets(listOf(SeekRange(1000, 5000), SeekRange(2000, 7000)), 0, 2500)
        assertEquals(listOf(2500L, 3500L), result.positions)
        assertEquals(false, result.limited)
    }

    @Test fun boundsMovementByEarliestBeginningOrEnd() {
        val ranges = listOf(SeekRange(1000, 5000), SeekRange(2000, 2500))
        assertEquals(LinkedSeekTargets(listOf(1500, 2500), true), linkedSeekTargets(ranges, 0, 4000))
        assertEquals(LinkedSeekTargets(listOf(0, 1000), true), linkedSeekTargets(ranges, 1, 0))
    }

    @Test fun unknownDurationDoesNotArtificiallyLimitForwardMovement() {
        assertEquals(LinkedSeekTargets(listOf(4000, 5000), false), linkedSeekTargets(listOf(SeekRange(1000, null), SeekRange(2000, null)), 0, 4000))
    }
}
