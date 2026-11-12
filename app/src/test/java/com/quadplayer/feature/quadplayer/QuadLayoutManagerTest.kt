package com.quadplayer.feature.quadplayer

import com.quadplayer.core.model.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Test

class QuadLayoutManagerTest {

    @Test
    fun initialState_isAlwaysEqualGrid() {
        val manager = QuadLayoutManager()

        assertEquals(LayoutMode.EQUAL_GRID, manager.layoutMode.value)
    }

    @Test
    fun reset_keepsEqualGridLayout() {
        val manager = QuadLayoutManager()

        manager.reset()

        assertEquals(LayoutMode.EQUAL_GRID, manager.layoutMode.value)
    }
}
