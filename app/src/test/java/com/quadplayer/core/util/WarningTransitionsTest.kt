package com.quadplayer.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WarningTransitionsTest {
    @Test fun warnsOnceUntilEachConditionClears() {
        val transitions = WarningTransitions()
        assertEquals(emptyList<PerformanceWarning>(), transitions.update(false, false))
        assertEquals(listOf(PerformanceWarning.LowMemory), transitions.update(true, false))
        assertEquals(emptyList<PerformanceWarning>(), transitions.update(true, false))
        assertEquals(listOf(PerformanceWarning.ThermalThrottle), transitions.update(true, true))
        assertEquals(emptyList<PerformanceWarning>(), transitions.update(false, true))
        assertEquals(listOf(PerformanceWarning.LowMemory), transitions.update(true, true))
    }
}
