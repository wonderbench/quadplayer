package com.quadplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueStateTest {
    @Test fun selectedMiddleItemKeepsPlaylistOrder() {
        val queue = PlaybackQueueState.from(listOf(4, 5, 6), 5)
        assertEquals(1, queue.index)
        assertTrue(queue.hasPrevious)
        assertTrue(queue.hasNext)
        assertFalse(queue.copy(index = 0).hasPrevious)
        assertFalse(queue.copy(index = 2).hasNext)
    }
    @Test fun standaloneVideoGetsSingleItemQueue() {
        val queue = PlaybackQueueState.from(listOf(4, 5), 8)
        assertEquals(listOf(8L), queue.ids)
        assertFalse(queue.hasPrevious)
        assertFalse(queue.hasNext)
    }
}
