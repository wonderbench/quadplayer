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
            override suspend fun deleteHistory(videoId: Long) {}
            override suspend fun clearHistory() {}
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
            override suspend fun deleteHistory(videoId: Long) {}
            override suspend fun clearHistory() {}
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
            override suspend fun deleteHistory(videoId: Long) {}
            override suspend fun clearHistory() {}
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

    @Test fun deletionWaitsForPendingSavesAndClearsResumeCache() = runTest {
        val permit = CompletableDeferred<Unit>()
        val stored = mutableMapOf<Long, Long>()
        val repository = object : HistoryRepository {
            override fun observeRecent(limit: Int): Flow<List<RecentPlayback>> = emptyFlow()
            override suspend fun getResumePosition(videoId: Long) = stored[videoId] ?: 0L
            override suspend fun recordPlayback(videoId: Long, positionMs: Long) { permit.await(); stored[videoId] = positionMs }
            override suspend fun deleteHistory(videoId: Long) { stored.remove(videoId) }
            override suspend fun clearHistory() { stored.clear() }
        }
        val queue = HistoryWriteQueue(repository, backgroundScope)
        queue.enqueue(1, 1000)
        val deletion = async { queue.delete(1) }
        runCurrent()
        assertFalse(deletion.isCompleted)
        permit.complete(Unit)
        deletion.await()
        assertEquals(0L, queue.getResumePosition(1))
        queue.enqueue(2, 2000)
        queue.clear()
        assertEquals(0L, queue.getResumePosition(2))
    }
}
