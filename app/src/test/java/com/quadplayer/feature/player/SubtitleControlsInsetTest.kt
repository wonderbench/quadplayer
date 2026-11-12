package com.quadplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleControlsInsetTest {
    @Test fun liftsOnlyWhenControlsOverlapPlayerAndRestoresDefaultWhenHidden() {
        assertEquals(0.08f, subtitleBottomPaddingFraction(100f, 1000, null), 0.0001f)
        assertEquals(0.08f, subtitleBottomPaddingFraction(100f, 1000, 1200f), 0.0001f)
        assertEquals(0.28f, subtitleBottomPaddingFraction(100f, 1000, 900f), 0.0001f)
        assertEquals(0.95f, subtitleBottomPaddingFraction(100f, 1000, 0f), 0.0001f)
        assertEquals(0.08f, subtitleBottomPaddingFraction(Float.NaN, 1000, 900f), 0.0001f)
    }
}
