package com.quadplayer.feature.quadplayer

import com.quadplayer.core.model.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Test

class QuadLayoutManagerTest {
    @Test fun focusAndResetKeepValidSelection() {
        val manager = QuadLayoutManager()
        assertEquals(LayoutMode.EQUAL_GRID, manager.state.value.mode)
        manager.focus(2)
        assertEquals(QuadLayoutState(LayoutMode.FOCUS_GRID, 2), manager.state.value)
        manager.focus(1, single = true)
        assertEquals(QuadLayoutState(LayoutMode.SINGLE, 1), manager.state.value)
        manager.focus(4)
        assertEquals(QuadLayoutState(LayoutMode.SINGLE, 1), manager.state.value)
        manager.reset()
        assertEquals(QuadLayoutState(), manager.state.value)
    }
}
