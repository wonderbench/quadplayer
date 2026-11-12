package com.quadplayer.core.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleStyleTest {
    @Test fun defaultsRemainCompatibleWithPreviouslySavedColors() {
        val style = SubtitleStyle()
        assertEquals(Color.White.toArgb().toLong(), style.foregroundColor)
        assertEquals(Color.Black.copy(alpha = 0.45f).toArgb().toLong(), style.backgroundColor)
    }
}
