package com.quadplayer.feature.quadplayer

import com.quadplayer.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioSourceManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun switchTo_withoutCrossfade_setsOnlySelectedTargetToFullVolume() = runTest {
        val manager = AudioSourceManager(this)
        val targets = List(4) { FakeVolumeTarget() }

        manager.switchTo(index = 2, targets = targets, useCrossfade = false)

        assertEquals(2, manager.currentAudioSource.value)
        assertEquals(0f, targets[0].lastVolume, 0.001f)
        assertEquals(0f, targets[1].lastVolume, 0.001f)
        assertEquals(1f, targets[2].lastVolume, 0.001f)
        assertEquals(0f, targets[3].lastVolume, 0.001f)
    }

    @Test
    fun switchTo_withCrossfade_finishesOnNewTarget() = runTest {
        val manager = AudioSourceManager(this)
        val targets = List(4) { FakeVolumeTarget() }

        manager.switchTo(index = 0, targets = targets, useCrossfade = false)
        manager.switchTo(index = 1, targets = targets, useCrossfade = true)
        advanceUntilIdle()

        assertEquals(1, manager.currentAudioSource.value)
        assertEquals(0f, targets[0].lastVolume, 0.001f)
        assertEquals(1f, targets[1].lastVolume, 0.001f)
        assertTrue(targets[1].history.size > 1)
    }

    @Test
    fun switchingAgain_cancelsPreviousCrossfadeAndKeepsLatestSource() = runTest {
        val manager = AudioSourceManager(this)
        val targets = List(4) { FakeVolumeTarget() }

        manager.switchTo(index = 0, targets = targets, useCrossfade = false)
        manager.switchTo(index = 1, targets = targets, useCrossfade = true)
        manager.switchTo(index = 3, targets = targets, useCrossfade = true)
        advanceUntilIdle()

        assertEquals(3, manager.currentAudioSource.value)
        assertEquals(0f, targets[0].lastVolume, 0.001f)
        assertEquals(0f, targets[1].lastVolume, 0.001f)
        assertEquals(0f, targets[2].lastVolume, 0.001f)
        assertEquals(1f, targets[3].lastVolume, 0.001f)
    }

    @Test
    fun muteAll_clearsCurrentAudioSourceAndSilencesAllTargets() = runTest {
        val manager = AudioSourceManager(this)
        val targets = List(4) { FakeVolumeTarget() }

        manager.switchTo(index = 1, targets = targets, useCrossfade = false)
        manager.muteAll(targets)

        assertEquals(-1, manager.currentAudioSource.value)
        targets.forEach { target ->
            assertEquals(0f, target.lastVolume, 0.001f)
        }
    }

    private class FakeVolumeTarget : VolumeTarget {
        var lastVolume: Float = -1f
        val history = mutableListOf<Float>()

        override fun setVolume(volume: Float) {
            lastVolume = volume
            history += volume
        }
    }
}
