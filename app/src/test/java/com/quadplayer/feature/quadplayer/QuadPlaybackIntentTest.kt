package com.quadplayer.feature.quadplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadPlaybackIntentTest {
    @Test fun bufferingUsesPlaybackIntentAndEndedShowsReplay() {
        assertTrue(shouldPauseOnToggle(playWhenReady = true, ended = false))
        assertFalse(shouldPauseOnToggle(playWhenReady = false, ended = false))
        assertFalse(shouldPauseOnToggle(playWhenReady = true, ended = true))
    }
}
