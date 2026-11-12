package com.quadplayer.core.data

import com.quadplayer.core.model.RecentPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import org.junit.Assert.assertFalse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryWriteQueueTest {
    @Test fun snapshotsAreWrittenInCaptureOrderAfterCallerReturns() = runTest {
        val writes = mutableListOf<Pair<Long, Long>>()
        val repository = object : HistoryRepository {
            override fun observeRecent(limit: Int): Flow<List<RecentPlayback>> = emptyFlow()
            override suspend fun getResumePosition(videoId: Long) = 0L
            override suspend fun recordPlayback(videoId: Long, positionMs: Long) { writes += videoId to positionMs }
        }
        val queue = HistoryWriteQueue(repository, backgroundScope)
        queue.enqueue(1, 1000)
        queue.enqueue(1, 2500)
        runCurrent()
        assertEquals(listOf(1L to 1000L, 1L to 2500L), writes)
    }
    @Test fun resumeWaitsForEarlierPendingSnapshot() = runTest {
        val permitWrite = CompletableDeferred<Unit>()
        var stored = 100L
        val repository = object : HistoryRepository {
            override fun observeRecent(limit: Int): Flow<List<RecentPlayback>> = emptyFlow()
            override suspend fun getResumePosition(videoId: Long) = stored
            override suspend fun recordPlayback(videoId: Long, positionMs: Long) {
                permitWrite.await()
                stored = positionMs
            }
        }
        val queue = HistoryWriteQueue(repository, backgroundScope)
        queue.enqueue(1, 2500)
        val reading = async { queue.getResumePosition(1) }
        runCurrent()
        assertFalse(reading.isCompleted)
        permitWrite.complete(Unit)
        runCurrent()
        assertEquals(2500L, reading.await())
    }

    @Test fun exitingBeforeLoadDoesNotOverwriteHistory() = runTest {
        val writes = mutableListOf<Long>()
        val repository = object : HistoryRepository {
            override fun observeRecent(limit: Int): Flow<List<RecentPlayback>> = emptyFlow()
            override suspend fun getResumePosition(videoId: Long) = 1200L
            override suspend fun recordPlayback(videoId: Long, positionMs: Long) { writes += positionMs }
        }
        val queue = HistoryWriteQueue(repository, backgroundScope)
        val session = PlaybackHistorySession(queue)
        session.capture(0)
        runCurrent()
        assertEquals(emptyList<Long>(), writes)
        session.markLoaded(1)
        session.capture(1500)
        runCurrent()
        assertEquals(listOf(1500L), writes)
    }

}
