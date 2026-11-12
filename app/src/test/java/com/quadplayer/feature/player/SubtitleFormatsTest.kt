package com.quadplayer.feature.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleFormatsTest {
    @Test fun recognizesProviderDisplayNameAndMime() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("Movie.SRT", "text/plain"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("subtitle.ass", null))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("12345", "application/x-subrip"))
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType(null, null))
    }
}
