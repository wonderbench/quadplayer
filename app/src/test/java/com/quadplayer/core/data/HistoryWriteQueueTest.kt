package com.quadplayer.core.data

import com.quadplayer.core.model.RecentPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
