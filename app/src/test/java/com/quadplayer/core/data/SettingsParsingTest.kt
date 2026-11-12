package com.quadplayer.core.data

import com.quadplayer.core.model.AppThemeMode
import com.quadplayer.core.model.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsParsingTest {
    @Test fun unknownAndMissingValuesFallBack() {
        assertEquals(DecoderMode.AUTO, savedEnumOrDefault("future-mode", DecoderMode.AUTO))
        assertEquals(AppThemeMode.SYSTEM, savedEnumOrDefault(null, AppThemeMode.SYSTEM))
        assertEquals(DecoderMode.SOFTWARE, savedEnumOrDefault("SOFTWARE", DecoderMode.AUTO))
    }
}
