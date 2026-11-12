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
import com.quadplayer.feature.player.PlaybackFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.coroutines.resume

data class QuadSlotUiState(
    val index: Int,
    val video: VideoFile? = null,
    val isAudioSource: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.X1_0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
    val failure: PlaybackFailure? = null,
    val playWhenReady: Boolean = false,
    val playbackEnded: Boolean = false,
    val repeatOne: Boolean = false,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class QuadPlaybackEngine(
    private val context: Context,
    private val awaitReadyOverride: (suspend (Player) -> Boolean)? = null,
    private val beforeLoad: suspend (VideoFile) -> Unit = {},
) {
    private data class Slot(val video: VideoFile? = null, val controller: PlayerController? = null)
    private val slots = List(4) { MutableStateFlow(Slot()) }
    private val volumes = MutableList(4) { 0f }
    private val pending = arrayOfNulls<PlayerController>(4)
    private val generations = LongArray(4)
    private var settings = AppSettings()
    private var globalSpeed: PlaybackSpeed? = null
    private var progressVisibleSlot = -1
    private var suspended = false
    private var interrupted = false
    private val resumeIntents = mutableMapOf<PlayerController, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private class SyncOperation(val playIntents: Map<PlayerController, Boolean>) { var job: Job? = null }
    private var synchronization: SyncOperation? = null

    fun controllerAt(index: Int): PlayerController? = slots[index].value.controller
    internal fun hasController(index: Int): Boolean = slots[index].value.controller != null

    fun slotStates(audioSourceFlow: Flow<Int>): Flow<List<QuadSlotUiState>> {
        val slotFlows = slots.mapIndexed { index, slot ->
            slot.flatMapLatest { current ->
                (current.controller?.uiState ?: flowOf(PlayerUiState())).map { state ->
                    QuadSlotUiState(
                        index = index, video = current.video, isPlaying = state.isPlaying,
                        playbackSpeed = state.playbackSpeed, positionMs = state.currentPositionMs,
                        durationMs = state.durationMs, errorMessage = state.errorMessage, failure = state.failure,
                        playWhenReady = state.playWhenReady,
                        playbackEnded = state.playbackState == PlaybackState.ENDED,
                        repeatOne = state.repeatOne,
                        loopStartMs = state.loopStartMs, loopEndMs = state.loopEndMs,
                    )
                }
            }
        }
        return combine(combine(slotFlows) { it.toList() }, audioSourceFlow) { states, audio ->
            states.map { it.copy(isAudioSource = it.index == audio) }
        }
    }

    suspend fun load(slotIndex: Int, video: VideoFile): Boolean {
        cancelSynchronization()
        cancelPending(slotIndex)
        val generation = generations[slotIndex]
        val old = slots[slotIndex].value.controller
        val controller = PlayerController(context, compactLocalBuffer = true)
        controller.setProgressUpdatesEnabled(false)
        pending[slotIndex] = controller
        try {
            controller.applySettings(settings)
            beforeLoad(video)
            if (generation != generations[slotIndex]) return false
            if (!controller.load(video, playWhenReady = false) || generation != generations[slotIndex]) return false
            if (slots[slotIndex].value.controller !== old) return false
            // A load can finish after synchronization has started for the old slot.
            cancelSynchronization()
            if (generation != generations[slotIndex] || slots[slotIndex].value.controller !== old) return false
            val selectedSpeed = old?.player?.playbackParameters?.speed?.let(PlaybackSpeed::fromValue) ?: globalSpeed
            selectedSpeed?.let(controller::setPlaybackSpeed)
            controller.player.volume = volumes[slotIndex]
            controller.setProgressUpdatesEnabled(slotIndex == progressVisibleSlot)
            slots[slotIndex].value = Slot(video, controller)
            pending[slotIndex] = null
            resumeIntents.remove(old)
            old?.release()
            setSlotPlaying(slotIndex, true)
            return true
        } finally {
            if (pending[slotIndex] === controller) pending[slotIndex] = null
            if (slots[slotIndex].value.controller !== controller) controller.release()
        }
    }

    private fun cancelPending(slotIndex: Int) {
        generations[slotIndex]++
        pending[slotIndex]?.release()
        pending[slotIndex] = null
    }

    fun cancelPendingLoads() { slots.indices.forEach(::cancelPending) }

    fun remove(slotIndex: Int) {
        cancelSynchronization()
        cancelPending(slotIndex)
        val controller = slots[slotIndex].value.controller
        slots[slotIndex].value = Slot()
        resumeIntents.remove(controller)
        controller?.release()
    }

    fun suspendPlayback() {
        if (suspended) return
        val priorIntents = synchronization?.playIntents
        cancelSynchronization(restore = false)
        suspended = true
        slots.mapNotNull { it.value.controller }.forEach { controller ->
            resumeIntents[controller] = priorIntents?.get(controller) ?: controller.player.playWhenReady
            controller.pause()
        }
    }

    fun resumePlayback() {
        if (!suspended) return
        suspended = false
        slots.mapNotNull { it.value.controller }.forEach { controller ->
            if (resumeIntents[controller] == true) controller.play()
        }
        resumeIntents.clear()
    }

    fun setSlotPlaying(slotIndex: Int, playing: Boolean) {
        cancelSynchronization()
        val controller = slots[slotIndex].value.controller ?: return
        val shouldPlay = playing && !interrupted
        if (suspended) {
            resumeIntents[controller] = shouldPlay
            controller.pause()
        } else if (shouldPlay) controller.play() else controller.pause()
    }

    fun hasPlayIntent(): Boolean = slots.mapNotNull { it.value.controller }.any {
        if (suspended) resumeIntents[it] == true else synchronization?.playIntents?.get(it) ?: it.player.playWhenReady
    }

    fun pauseAll() {
        slots.indices.forEach { setSlotPlaying(it, false) }
    }

    fun interruptPlayback() {
        cancelSynchronization(restore = false)
        interrupted = true
        pauseAll()
    }

    fun allowPlayback() { interrupted = false }

    fun isInterrupted(): Boolean = interrupted

    fun swap(first: Int, second: Int) {
        if (first == second) return
        cancelSynchronization()
        cancelPending(first)
        cancelPending(second)
        val firstSlot = slots[first].value
        slots[first].value = slots[second].value
        slots[second].value = firstSlot
        setProgressVisibleSlot(progressVisibleSlot)
    }

    fun setProgressVisibleSlot(index: Int) {
        progressVisibleSlot = index
        slots.forEachIndexed { slotIndex, slot -> slot.value.controller?.setProgressUpdatesEnabled(slotIndex == index) }
    }

    fun togglePlayAll() {
        cancelSynchronization()
        val anyPlaying = hasPlayIntent()
        slots.indices.forEach { index -> setSlotPlaying(index, !anyPlaying) }
    }

    fun currentPosition(slotIndex: Int): Long? = slots[slotIndex].value.controller?.player?.currentPosition

    fun toggleSlot(slotIndex: Int) {
        cancelSynchronization()
        val controller = slots[slotIndex].value.controller ?: return
        val player = controller.player
        val playing = if (suspended) resumeIntents[controller] == true else player.playWhenReady
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekToDefaultPosition()
            setSlotPlaying(slotIndex, true)
        } else setSlotPlaying(slotIndex, !shouldPauseOnToggle(playing, ended = false))
    }

    fun retrySlot(slotIndex: Int) {
        val controller = slots[slotIndex].value.controller ?: return
        controller.retry(playWhenReady = false)
        setSlotPlaying(slotIndex, true)
    }

    fun setSlotSpeed(slotIndex: Int, speed: PlaybackSpeed) {
        slots[slotIndex].value.controller?.setPlaybackSpeed(speed)
    }

    fun setSlotRepeat(slotIndex: Int, enabled: Boolean) {
        cancelSynchronization()
        val controller = slots[slotIndex].value.controller ?: return
        if (enabled) controller.clearLoop()
        controller.player.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun setLoopStart(slotIndex: Int): Boolean { cancelSynchronization(); return slots[slotIndex].value.controller?.setLoopStart() ?: false }
    fun setLoopEnd(slotIndex: Int): Boolean { cancelSynchronization(); return slots[slotIndex].value.controller?.setLoopEnd() ?: false }
    fun clearLoop(slotIndex: Int) { cancelSynchronization(); slots[slotIndex].value.controller?.clearLoop() }

    /** Pauses populated slots, aligns followers and restores only their previous play intents. */
    fun synchronize(sourceIndex: Int, onFinished: (Boolean) -> Unit): Boolean {
        cancelSynchronization()
        if (suspended || interrupted) return false
        val source = slots.getOrNull(sourceIndex)?.value?.controller ?: return false
        val controllers = slots.mapNotNull { it.value.controller }
        val followers = controllers.filter { it !== source }
        if (controllers.any { it.uiState.value.loopEndMs != null } || followers.any { !it.player.isCurrentMediaItemSeekable }) return false
        val op = SyncOperation(controllers.associateWith { it.player.playWhenReady })
        synchronization = op
        controllers.forEach {
            if (synchronization !== op) return false
            it.pause()
        }
        if (synchronization !== op) return false
        val target = source.player.currentPosition.coerceAtLeast(0L)
        val limited = followers.any { it.player.duration > 0 && target >= it.player.duration }
        op.job = scope.launch(start = CoroutineStart.LAZY) {
            val success = try {
                followers.forEach { controller ->
                    if (synchronization !== op) return@launch
                    val duration = controller.player.duration
                    val position = if (duration > 0) target.coerceAtMost((duration - 1).coerceAtLeast(0)) else target
                    controller.seekTo(position)
                }
                if (synchronization !== op) return@launch
                withTimeoutOrNull(10_000) {
                    coroutineScope {
                        followers.map { controller -> async { awaitReadyOverride?.invoke(controller.player) ?: awaitSeekReady(controller.player) } }.awaitAll().all { it }
                    }
                } == true && !limited
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) { false }
            if (synchronization === op) {
                synchronization = null
                restoreSyncIntents(op)
                onFinished(success)
            }
        }
        if (synchronization !== op) { op.job?.cancel(); return false }
        op.job?.start()
        return true
    }

    fun cancelSynchronization() = cancelSynchronization(restore = true)

    private fun cancelSynchronization(restore: Boolean) {
        val op = synchronization ?: return
        synchronization = null
        op.job?.cancel()
        if (restore && !suspended && !interrupted) restoreSyncIntents(op)
    }

    private fun restoreSyncIntents(op: SyncOperation) {
        val present = slots.mapNotNull { it.value.controller }.toSet()
        op.playIntents.forEach { (controller, playing) ->
            if (controller in present) {
                if (playing) controller.play() else controller.pause()
            }
        }
    }

    private suspend fun awaitSeekReady(player: Player): Boolean {
        if (player.playerError != null || player.playbackState == Player.STATE_ENDED) return false
        if (player.playbackState == Player.STATE_READY) return true
        return suspendCancellableCoroutine { continuation ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        player.removeListener(this)
                        if (continuation.isActive) continuation.resume(playbackState == Player.STATE_READY)
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    player.removeListener(this)
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            player.addListener(listener)
            continuation.invokeOnCancellation { player.removeListener(listener) }
        }
    }

    fun seekSlot(slotIndex: Int, positionMs: Long) {
        cancelSynchronization()
        slots[slotIndex].value.controller?.seekTo(positionMs)
    }

    /** Null means at least one loaded video is not yet seekable. */
    fun seekLinked(slotIndex: Int, positionMs: Long): Boolean? {
        cancelSynchronization()
        val source = slots[slotIndex].value.controller ?: return null
        val controllers = slots.mapNotNull { it.value.controller }
        if (controllers.any { !it.player.isCurrentMediaItemSeekable }) return null
        val sourceIndex = controllers.indexOf(source)
        val ranges = controllers.map { controller ->
            val duration = controller.player.duration.takeIf { it > 0 }
            SeekRange(controller.player.currentPosition.coerceAtLeast(0L), duration)
        }
        val targets = linkedSeekTargets(ranges, sourceIndex, positionMs)
        controllers.zip(targets.positions).forEach { (controller, target) -> controller.seekTo(target) }
        return targets.limited
    }

    suspend fun applySettings(settings: AppSettings) {
        cancelSynchronization()
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
            if (suspended) resumeIntents[controller] ?: false else synchronization?.playIntents?.get(controller) ?: controller.player.playWhenReady,
        )
    }

    fun setVolume(index: Int, volume: Float) {
        volumes[index] = volume
        slots[index].value.controller?.player?.volume = volume
    }

    fun release() {
        cancelSynchronization(restore = false)
        scope.cancel()
        slots.forEachIndexed { index, _ -> remove(index) }
    }
}

class QuadVolumeTarget(private val engine: QuadPlaybackEngine, private val index: Int) : VolumeTarget {
    override fun setVolume(volume: Float) = engine.setVolume(index, volume)
}

internal fun shouldPauseOnToggle(playWhenReady: Boolean, ended: Boolean): Boolean = playWhenReady && !ended
