package com.quadplayer.feature.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class SubtitleFormatsTest {
    @Test fun findsReadableSiblingAndIgnoresMissingOrDirectory() {
        val dir = Files.createTempDirectory("quad-subtitle-test").toFile()
        val video = dir.resolve("Movie.mp4").apply { writeText("video") }
        val subtitle = dir.resolve("Movie.srt").apply { writeText("subtitle") }
        try {
            assertEquals(subtitle, findLocalSubtitle(video.path))
            subtitle.delete()
            subtitle.mkdir()
            assertEquals(null, findLocalSubtitle(video.path))
            assertEquals(null, findLocalSubtitle(null))
        } finally { subtitle.delete(); video.delete(); dir.delete() }
    }
    @Test fun recognizesProviderDisplayNameAndMime() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("Movie.SRT", "text/plain"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("subtitle.ass", null))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("12345", "application/x-subrip"))
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType(null, null))
    }
}
