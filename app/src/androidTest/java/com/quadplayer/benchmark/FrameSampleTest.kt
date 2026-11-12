package com.quadplayer.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameSampleTest {
    @Test fun rejectsStaleOrMissingStreams() {
        val now = 10_000_000_000L
        val fresh = FrameSample(10_000_000, now, now)
        val stalled = FrameSample(1_000_000, 1_000_000_000L, 1_000_000_000L)
        assertNull(estimateDriftMs(listOf(fresh, stalled), now, 30))
        assertNull(estimateDriftMs(listOf(fresh, null), now, 30))
    }

    @Test fun comparesFreshPresentationAndReleasePairs() {
        val now = 10_000_000_000L
        val first = FrameSample(9_900_000, now - 100_000_000L, now - 100_000_000L)
        val second = FrameSample(9_850_000, now - 100_000_000L, now - 100_000_000L)
        assertEquals(50L, estimateDriftMs(listOf(first, second), now, 30))
    }
}
