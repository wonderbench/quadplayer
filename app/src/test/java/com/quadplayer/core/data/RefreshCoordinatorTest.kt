package com.quadplayer.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshCoordinatorTest {
    @Test fun overlappingRequestsShareWorkAndRecheckAfterChanges() = runTest {
        val releaseFirst = CompletableDeferred<Unit>()
        var scans = 0
        val coordinator = RefreshCoordinator(backgroundScope) {
            scans++
            if (scans == 1) releaseFirst.await()
        }
        val first = async { coordinator.refresh() }
        runCurrent()
        val second = async { coordinator.refresh() }
        val third = async { coordinator.refresh() }
        runCurrent()
        assertEquals(1, scans)
        releaseFirst.complete(Unit)
        runCurrent()
        first.await(); second.await(); third.await()
        assertEquals(2, scans)
        val next = async { coordinator.refresh() }
        runCurrent(); next.await()
        assertEquals(3, scans)
    }
}
