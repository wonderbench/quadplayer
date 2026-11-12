package com.quadplayer.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCapabilityCheckerTest {
    @Test fun warnsOnlyWhenActualFormatHasNoHardwareSupport() {
        assertEquals(VideoCapabilityWarning.NoSupportedDecoder, assessDecoderSupport(emptyList()))
        assertEquals(VideoCapabilityWarning.SoftwareOnly, assessDecoderSupport(listOf(DecoderSupport(false))))
        assertNull(assessDecoderSupport(listOf(DecoderSupport(true))))
        assertNull(assessDecoderSupport(listOf(DecoderSupport(true), DecoderSupport(false))))
    }
}
