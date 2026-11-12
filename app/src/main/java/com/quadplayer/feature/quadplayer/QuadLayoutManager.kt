package com.quadplayer.feature.quadplayer

import com.quadplayer.core.model.LayoutMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QuadLayoutState(val mode: LayoutMode = LayoutMode.EQUAL_GRID, val focusedSlot: Int = 0)

class QuadLayoutManager {
    private val _state = MutableStateFlow(QuadLayoutState())
    val state: StateFlow<QuadLayoutState> = _state.asStateFlow()

    fun focus(slot: Int, single: Boolean = false) {
        if (slot !in 0..3) return
        _state.value = QuadLayoutState(if (single) LayoutMode.SINGLE else LayoutMode.FOCUS_GRID, slot)
    }

    fun reset() { _state.value = QuadLayoutState() }
}
