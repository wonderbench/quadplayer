package com.quadplayer.feature.quadplayer

import com.quadplayer.core.model.LayoutMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QuadLayoutManager {
    private val _layoutMode = MutableStateFlow(LayoutMode.EQUAL_GRID)
    val layoutMode: StateFlow<LayoutMode> = _layoutMode.asStateFlow()

    fun reset() {
        _layoutMode.value = LayoutMode.EQUAL_GRID
    }
}
