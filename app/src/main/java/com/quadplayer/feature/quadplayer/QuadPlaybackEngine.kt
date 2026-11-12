package com.quadplayer.feature.quadplayer

import android.content.Context
import com.quadplayer.core.data.SavedQuadSlot
import androidx.media3.common.Player
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.PlaybackState
import com.quadplayer.core.model.VideoFile
import com.quadplayer.feature.player.PlayerController
import com.quadplayer.feature.player.PlayerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class QuadSlotUiState(
    val index: Int,
    val video: VideoFile? = null,
    val isAudioSource: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.X1_0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
    val playWhenReady: Boolean = false,
    val playbackEnded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class QuadPlaybackEngine(private val context: Context) {
    private data class Slot(val video: VideoFile? = null, val controller: PlayerController? = null)
    private val slots = List(4) { MutableStateFlow(Slot()) }
    private val volumes = MutableList(4) { 0f }
    private var settings = AppSettings()
    private var globalSpeed: PlaybackSpeed? = null

    fun controllerAt(index: Int): PlayerController? = slots[index].value.controller
    internal fun hasController(index: Int): Boolean = slots[index].value.controller != null

    fun slotStates(audioSourceFlow: Flow<Int>): Flow<List<QuadSlotUiState>> {
        val slotFlows = slots.mapIndexed { index, slot ->
            slot.flatMapLatest { current ->
                (current.controller?.uiState ?: flowOf(PlayerUiState())).map { state ->
                    QuadSlotUiState(
                        index = index, video = current.video, isPlaying = state.isPlaying,
                        playbackSpeed = state.playbackSpeed, positionMs = state.currentPositionMs,
                        durationMs = state.durationMs, errorMessage = state.errorMessage,
                        playWhenReady = state.playWhenReady,
                        playbackEnded = state.playbackState == PlaybackState.ENDED,
                    )
                }
            }
        }
        return combine(combine(slotFlows) { it.toList() }, audioSourceFlow) { states, audio ->
            states.map { it.copy(isAudioSource = it.index == audio) }
        }
    }

    suspend fun load(slotIndex: Int, video: VideoFile) {
        val old = slots[slotIndex].value.controller
        val controller = old ?: PlayerController(context).also {
            it.applySettings(settings)
            globalSpeed?.let(it::setPlaybackSpeed)
            it.player.volume = volumes[slotIndex]
        }
        try {
            controller.load(video)
            slots[slotIndex].value = Slot(video, controller)
        } catch (error: Throwable) {
            if (old == null) controller.release()
            throw error
        }
    }

    fun remove(slotIndex: Int) {
        val controller = slots[slotIndex].value.controller
        slots[slotIndex].value = Slot()
        controller?.release()
    }

    suspend fun swap(first: Int, second: Int) {
        if (first == second) return
        val firstSlot = slots[first].value
        slots[first].value = slots[second].value
        slots[second].value = firstSlot
    }

    fun togglePlayAll() {
        val populated = slots.mapNotNull { it.value.controller }
        val anyPlaying = populated.any { it.player.playWhenReady }
        populated.forEach { if (anyPlaying) it.pause() else it.play() }
    }

    fun currentPosition(slotIndex: Int): Long? = slots[slotIndex].value.controller?.player?.currentPosition

    fun toggleSlot(slotIndex: Int) {
        val player = slots[slotIndex].value.controller?.player ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekToDefaultPosition()
            player.play()
        } else if (shouldPauseOnToggle(player.playWhenReady, ended = false)) player.pause() else player.play()
    }

    fun setSlotSpeed(slotIndex: Int, speed: PlaybackSpeed) {
        slots[slotIndex].value.controller?.setPlaybackSpeed(speed)
    }

    fun syncToPosition(positionMs: Long) {
        slots.forEach { slot ->
            slot.value.controller?.let { controller ->
                val duration = controller.player.duration
                controller.seekTo(if (duration > 0) positionMs.coerceAtMost(duration) else positionMs)
            }
        }
    }

    fun seekSlot(slotIndex: Int, positionMs: Long) {
        slots[slotIndex].value.controller?.seekTo(positionMs)
    }

    suspend fun applySettings(settings: AppSettings) {
        this.settings = settings
        slots.forEach { it.value.controller?.applySettings(settings) }
    }

    fun setGlobalSpeed(speed: PlaybackSpeed) {
        globalSpeed = speed
        slots.forEach { it.value.controller?.setPlaybackSpeed(speed) }
    }

    fun snapshot(): List<SavedQuadSlot?> = slots.map { slot ->
        val current = slot.value
        val controller = current.controller
        val video = current.video
        if (controller == null || video == null) null else SavedQuadSlot(
            video.uriString,
            controller.player.currentPosition.coerceAtLeast(0L),
            controller.player.playbackParameters.speed,
            controller.player.playWhenReady,
        )
    }

    fun setVolume(index: Int, volume: Float) {
        volumes[index] = volume
        slots[index].value.controller?.player?.volume = volume
    }

    fun release() {
        slots.forEachIndexed { index, _ -> remove(index) }
    }
}

class QuadVolumeTarget(private val engine: QuadPlaybackEngine, private val index: Int) : VolumeTarget {
    override fun setVolume(volume: Float) = engine.setVolume(index, volume)
}

internal fun shouldPauseOnToggle(playWhenReady: Boolean, ended: Boolean): Boolean = playWhenReady && !ended
