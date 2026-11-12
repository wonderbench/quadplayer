package com.quadplayer.feature.quadplayer

import com.quadplayer.core.data.SavedQuadSession
import com.quadplayer.core.data.SavedQuadSlot
import com.quadplayer.core.model.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadRecoveryPolicyTest {
    private val session = SavedQuadSession(listOf(SavedQuadSlot("content://video/1", 100, 1f, true), null, null, null), LayoutMode.EQUAL_GRID, 0, 0)
    @Test fun dismissingDoesNotAuthorizeOverwritingTheOldSession() {
        val offered = RecoveryPrompt().offer(session)
        val hidden = offered.hide()
        assertFalse(hidden.visible)
        assertFalse(hidden.maySave)
        assertEquals(session, hidden.reopen().session)
        assertTrue(hidden.reopen().visible)
        assertTrue(hidden.discard().maySave)
    }
    @Test fun missingAudioFallsBackButExplicitMuteIsPreserved() {
        assertEquals(1, recoveredAudioSource(0, listOf(1, 2)))
        assertEquals(2, recoveredAudioSource(2, listOf(1, 2)))
        assertEquals(-1, recoveredAudioSource(0, emptyList()))
        assertEquals(-1, recoveredAudioSource(-1, listOf(1)))
    }
}
