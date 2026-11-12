package com.quadplayer.feature.quadplayer

import android.content.Context
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.VideoFile
import com.quadplayer.feature.player.PlayerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class QuadSlotUiState(
    val index: Int,
    val video: VideoFile? = null,
    val isAudioSource: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
)

class QuadPlaybackEngine(
    context: Context,
) {
    private val controllers = List(4) { PlayerController(context) }
    private val slotVideos = MutableList<VideoFile?>(4) { null }

    fun controllerAt(index: Int): PlayerController = controllers[index]

    fun slotStates(audioSourceFlow: Flow<Int>): Flow<List<QuadSlotUiState>> {
        return combine(
            audioSourceFlow,
            controllers[0].uiState,
            controllers[1].uiState,
            controllers[2].uiState,
            controllers[3].uiState,
        ) { audioSource, s0, s1, s2, s3 ->
            listOf(s0, s1, s2, s3).mapIndexed { index, state ->
                QuadSlotUiState(
                    index = index,
                    video = slotVideos[index],
                    isAudioSource = audioSource == index,
                    isPlaying = state.isPlaying,
                    positionMs = state.currentPositionMs,
                    durationMs = state.durationMs,
                    errorMessage = state.errorMessage,
                )
            }
        }
    }

    suspend fun load(slotIndex: Int, video: VideoFile) {
        slotVideos[slotIndex] = video
        controllers[slotIndex].load(video)
    }

    fun remove(slotIndex: Int) {
        slotVideos[slotIndex] = null
        controllers[slotIndex].clear()
    }

    suspend fun swap(first: Int, second: Int) {
        if (first == second) return
        val firstVideo = slotVideos[first]
        val secondVideo = slotVideos[second]
        val firstPosition = controllers[first].player.currentPosition
        val secondPosition = controllers[second].player.currentPosition
        val firstPlaying = controllers[first].player.playWhenReady
        val secondPlaying = controllers[second].player.playWhenReady
        controllers[first].clear()
        controllers[second].clear()
        slotVideos[first] = secondVideo
        slotVideos[second] = firstVideo
        if (secondVideo != null) {
            controllers[first].load(secondVideo, secondPosition)
            if (!secondPlaying) controllers[first].pause()
        }
        if (firstVideo != null) {
            controllers[second].load(firstVideo, firstPosition)
            if (!firstPlaying) controllers[second].pause()
        }
    }

    fun togglePlayAll() {
        val populated = controllers.filterIndexed { index, _ -> slotVideos[index] != null }
        val anyPlaying = populated.any { it.player.playWhenReady }
        populated.forEach { controller ->
            if (anyPlaying) controller.pause() else controller.play()
        }
    }

    fun syncToPosition(positionMs: Long) {
        controllers.forEachIndexed { index, controller ->
            if (slotVideos[index] != null) {
                controller.seekTo(positionMs)
            }
        }
    }

    fun seekSlot(slotIndex: Int, positionMs: Long) {
        if (slotVideos[slotIndex] != null) {
            controllers[slotIndex].seekTo(positionMs)
        }
    }

    suspend fun applySettings(settings: AppSettings) {
        controllers.forEach { controller ->
            controller.applySettings(settings)
        }
    }

    fun setGlobalSpeed(speed: PlaybackSpeed) {
        controllers.forEach { controller -> controller.setPlaybackSpeed(speed) }
    }

    fun release() {
        controllers.forEach(PlayerController::release)
    }
}

class QuadVolumeTarget(
    private val controller: PlayerController,
) : VolumeTarget {
    override fun setVolume(volume: Float) {
        controller.player.volume = volume
    }
}
