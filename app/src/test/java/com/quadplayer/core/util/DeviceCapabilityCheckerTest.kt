package com.quadplayer.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCapabilityCheckerTest {
    @Test fun distinguishesUnsupportedSoftwareAndReportedLimits() {
        assertEquals(VideoCapabilityWarning.NoSupportedDecoder, assessDecoderSupport(emptyList(), 1))
        assertEquals(VideoCapabilityWarning.SoftwareOnly, assessDecoderSupport(listOf(DecoderSupport(false, 8)), 1))
        assertEquals(VideoCapabilityWarning.ConcurrentLimit(3, 2), assessDecoderSupport(listOf(DecoderSupport(true, 2)), 3))
        assertNull(assessDecoderSupport(listOf(DecoderSupport(true, null)), 4))
        assertNull(assessDecoderSupport(listOf(DecoderSupport(true, 4)), 4))
    }
}
