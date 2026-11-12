package com.quadplayer.feature.quadplayer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface VolumeTarget {
    fun setVolume(volume: Float)
}

class AudioSourceManager(
    private val scope: CoroutineScope,
) {
    private val _currentAudioSource = MutableStateFlow(-1)
    val currentAudioSource: StateFlow<Int> = _currentAudioSource.asStateFlow()
    private var crossfadeJob: Job? = null

    fun switchTo(index: Int, targets: List<VolumeTarget>, useCrossfade: Boolean) {
        if (index !in targets.indices) return
        val previous = _currentAudioSource.value
        _currentAudioSource.value = index
        crossfadeJob?.cancel()
        if (!useCrossfade || previous == -1 || previous == index) {
            targets.forEachIndexed { targetIndex, target ->
                target.setVolume(if (targetIndex == index) 1f else 0f)
            }
            return
        }

        crossfadeJob = scope.launch(Dispatchers.Main.immediate) {
            repeat(5) { step ->
                val progress = (step + 1) / 5f
                targets[previous].setVolume(1f - progress)
                targets[index].setVolume(progress)
                delay(30)
            }
            targets.forEachIndexed { targetIndex, target ->
                target.setVolume(if (targetIndex == index) 1f else 0f)
            }
        }
    }

    fun muteAll(targets: List<VolumeTarget>) {
        crossfadeJob?.cancel()
        _currentAudioSource.value = -1
        targets.forEach { it.setVolume(0f) }
    }
}
