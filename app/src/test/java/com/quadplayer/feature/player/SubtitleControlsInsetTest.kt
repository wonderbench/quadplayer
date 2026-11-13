package com.quadplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleControlsInsetTest {
    @Test fun liftsOnlyWhenControlsOverlapPlayerAndRestoresDefaultWhenHidden() {
        assertEquals(0, subtitleControlsInset(100f, 1000, null))
        assertEquals(0, subtitleControlsInset(100f, 1000, 1200f))
        assertEquals(200, subtitleControlsInset(100f, 1000, 900f))
        assertEquals(1000, subtitleControlsInset(100f, 1000, 0f))
        assertEquals(0, subtitleControlsInset(Float.NaN, 1000, 900f))
        assertEquals(1, subtitleControlsInset(0f, 400, 399.7f))
    }
}
