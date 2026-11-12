package com.quadplayer.feature.quadplayer

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.R
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.data.QuadSessionRepository
import com.quadplayer.core.data.QuadPreset
import com.quadplayer.core.data.QuadPresetRepository
import com.quadplayer.core.data.SavePresetResult
import com.quadplayer.core.data.SavedQuadSession
import com.quadplayer.core.data.VideoRepository
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.LayoutMode
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.util.DeviceCapabilityChecker
import com.quadplayer.core.util.PerformanceMonitor
import com.quadplayer.core.util.PerformanceWarning
import com.quadplayer.core.util.enqueueWarning
import com.quadplayer.core.util.VideoCapabilityWarning
import com.quadplayer.feature.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

data class QuadNotice(@StringRes val resourceId: Int, val arguments: List<Any> = emptyList(), val isError: Boolean = true)

data class QuadPlayerUiState(
    val slots: List<QuadSlotUiState> = List(4) { QuadSlotUiState(index = it) },
    val layoutMode: LayoutMode = LayoutMode.EQUAL_GRID,
    val focusedSlot: Int = 0,
    val settings: AppSettings = AppSettings(),
    val warningMessage: QuadNotice? = null,
    val pendingSwapSource: Int? = null,
    val offerRecovery: Boolean = false,
    val recoveryAvailable: Boolean = false,
    val linkedSeek: Boolean = false,
    val presets: List<QuadPreset> = emptyList(),
    val restoring: Boolean = false,
    val presetsReady: Boolean = false,
)

@HiltViewModel
class QuadPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: QuadSessionRepository,
    private val presetRepository: QuadPresetRepository,
    private val capabilityChecker: DeviceCapabilityChecker,
    private val performanceMonitor: PerformanceMonitor,
) : ViewModel() {
    private val engine = QuadPlaybackEngine(context)
    private val audioFocus = QuadAudioFocus(context) { engine.interruptPlayback() }
    private val audioSourceManager = AudioSourceManager(viewModelScope)
    private val layoutManager = QuadLayoutManager()
    private val warningMessage = MutableStateFlow<QuadNotice?>(null)
    private val capabilityWarning = MutableStateFlow<QuadNotice?>(null)
    private val performanceWarnings = MutableStateFlow<List<PerformanceWarning>>(emptyList())
    private val queuedNotices = combine(capabilityWarning, performanceWarnings) { capability, performance ->
        capability ?: when (performance.firstOrNull()) {
            PerformanceWarning.LowMemory -> QuadNotice(R.string.quad_warning_low_memory)
            PerformanceWarning.ThermalThrottle -> QuadNotice(R.string.quad_warning_thermal)
            null -> null
        }
    }
    private val pendingSwapSource = MutableStateFlow<Int?>(null)
    private val linkedSeek = MutableStateFlow(false)
    private val recoveryPrompt = MutableStateFlow(RecoveryPrompt())
    private var sessionReady = false
    private val sessionInitialized = MutableStateFlow(false)
    private var restoring = false
    private val restoreBusy = MutableStateFlow(false)
    private var released = false
    private val importGenerations = LongArray(4)
    private val volumeTargets = List(4) { QuadVolumeTarget(engine, it) }

    private val baseUiState = combine(
        engine.slotStates(audioSourceManager.currentAudioSource),
        layoutManager.state,
        settingsRepository.settings,
        linkedSeek,
    ) { slots, layout, settings, linked ->
        QuadPlayerUiState(
            slots = slots,
            layoutMode = layout.mode,
            focusedSlot = layout.focusedSlot,
            settings = settings,
            linkedSeek = linked,
        )
    }

    private val uiWithPresets = combine(baseUiState, presetRepository.presets, restoreBusy, sessionInitialized) { base, presets, busy, initialized ->
        base.copy(presets = presets, restoring = busy, presetsReady = initialized && !busy)
    }

    val uiState: StateFlow<QuadPlayerUiState> = combine(
        uiWithPresets,
        warningMessage,
        queuedNotices,
        pendingSwapSource,
        recoveryPrompt,
    ) { baseState, warning, queuedNotice, pendingSwap, recovery ->
        baseState.copy(
            warningMessage = warning ?: queuedNotice,
            pendingSwapSource = pendingSwap,
            offerRecovery = recovery.session != null && recovery.visible,
            recoveryAvailable = recovery.session != null && !recovery.visible && !recovery.busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QuadPlayerUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                engine.applySettings(settings)
            }
        }

        viewModelScope.launch {
            val capability = try {
                withContext(Dispatchers.IO) { capabilityChecker.check() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w("QuadPlayerViewModel", "Device capability check failed", failure)
                null
            }
            if (capability?.warning != null) {
                capabilityWarning.value = QuadNotice(
                    R.string.quad_warning_capability,
                    listOf(capability.totalRamMb, capability.maxConcurrentDecoders),
                )
            }
        }

        performanceMonitor.start(context, viewModelScope)
        viewModelScope.launch {
            performanceMonitor.warnings.collect { warning ->
                performanceWarnings.value = enqueueWarning(performanceWarnings.value, warning)
            }
        }

        audioSourceManager.muteAll(volumeTargets)
        layoutManager.reset()
        viewModelScope.launch {
            try {
                val previous = sessionRepository.read()
                if (engine.snapshot().all { it == null } && previous?.slots?.any { it != null } == true) {
                    recoveryPrompt.value = recoveryPrompt.value.offer(previous)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                warningMessage.value = QuadNotice(R.string.quad_session_read_failed)
            } finally { sessionReady = true; sessionInitialized.value = true }
        }
        viewModelScope.launch {
            while (true) {
                delay(10000)
                saveSession()
            }
        }
        viewModelScope.launch {
            engine.slotStates(audioSourceManager.currentAudioSource).collect {
                val source = audioSourceManager.currentAudioSource.value
                if (source in 0..3 && engine.controllerAt(source)?.uiState?.value?.errorMessage != null) {
                    engine.cancelSynchronization()
                    val fallback = (0..3).firstOrNull { index ->
                        index != source && engine.controllerAt(index)?.let { it.uiState.value.errorMessage == null } == true
                    }
                    if (fallback != null) audioSourceManager.switchTo(fallback, volumeTargets, false)
                    else audioSourceManager.muteAll(volumeTargets)
                    warningMessage.value = QuadNotice(R.string.quad_audio_source_failed)
                }
            }
        }
    }

    fun controllerAt(index: Int): PlayerController? = engine.controllerAt(index)
    fun setProgressVisibleSlot(index: Int) = engine.setProgressVisibleSlot(index)

    fun onScreenStopped() {
        engine.suspendPlayback()
        audioFocus.stop()
        saveSession()
    }

    fun onScreenStarted() {
        audioFocus.start()
        if (engine.hasPlayIntent() && audioSourceManager.currentAudioSource.value >= 0 && !requestAudioFocus()) engine.pauseAll()
        engine.resumePlayback()
    }

    private fun requestAudioFocus(): Boolean {
        if (audioFocus.request()) return true
        warningMessage.value = QuadNotice(R.string.quad_audio_focus_unavailable)
        return false
    }

    fun addVideo(slotIndex: Int, uri: Uri) {
        if (restoring) return
        val request = ++importGenerations[slotIndex]
        viewModelScope.launch {
            try {
                val video = videoRepository.importVideo(uri)
                if (request != importGenerations[slotIndex]) return@launch
                if (video == null) {
                    warningMessage.value = QuadNotice(R.string.quad_import_failed, listOf(slotIndex + 1))
                    return@launch
                }
                if (!engine.isInterrupted() && audioFocus.active && !requestAudioFocus()) return@launch
                if (!loadAndInspect(slotIndex, video) || request != importGenerations[slotIndex]) return@launch
                val settings = uiState.value.settings
                if (audioSourceManager.currentAudioSource.value == -1) {
                    audioSourceManager.switchTo(slotIndex, volumeTargets, settings.crossfadeAudioSwitching)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                warningMessage.value = QuadNotice(R.string.quad_import_failed, listOf(slotIndex + 1))
            }
        }
    }

    private suspend fun loadAndInspect(slotIndex: Int, video: com.quadplayer.core.model.VideoFile): Boolean {
        if (!engine.load(slotIndex, video)) return false
        viewModelScope.launch {
            val warning = withContext(Dispatchers.IO) { capabilityChecker.inspectVideo(video.uri) }
            if (engine.snapshot().getOrNull(slotIndex)?.uri == video.uriString) {
                when (warning) {
                    VideoCapabilityWarning.NoSupportedDecoder -> warningMessage.value = QuadNotice(R.string.quad_warning_format)
                    VideoCapabilityWarning.SoftwareOnly -> warningMessage.value = QuadNotice(R.string.quad_warning_software)
                    null -> Unit
                }
            }
        }
        return true
    }

    fun removeSlot(slotIndex: Int) {
        if (restoring) return
        importGenerations[slotIndex]++
        engine.remove(slotIndex)
        if (audioSourceManager.currentAudioSource.value == slotIndex) {
            audioSourceManager.muteAll(volumeTargets)
        }
    }

    fun toggleAudioSource(slotIndex: Int) {
        if (restoring) return
        engine.cancelSynchronization()
        if (engine.hasPlayIntent() && !requestAudioFocus()) return
        val settings = uiState.value.settings
        audioSourceManager.switchTo(slotIndex, volumeTargets, settings.crossfadeAudioSwitching)
    }

    fun muteAll() {
        if (restoring) return
        engine.cancelSynchronization()
        audioSourceManager.muteAll(volumeTargets)
        audioFocus.abandon()
    }

    fun togglePlayAll() {
        if (restoring) return
        if (!engine.hasPlayIntent() && audioSourceManager.currentAudioSource.value >= 0 && !requestAudioFocus()) return
        engine.allowPlayback()
        engine.togglePlayAll()
    }

    fun syncToAudioSource() {
        if (restoring) return
        val sourceIndex = audioSourceManager.currentAudioSource.value
        if (sourceIndex !in 0..3) return
        val started = engine.synchronize(sourceIndex) { success ->
            if (!success) warningMessage.value = QuadNotice(R.string.quad_sync_incomplete)
        }
        if (!started) warningMessage.value = QuadNotice(R.string.quad_sync_not_ready)
    }

    fun setSlotRepeat(slotIndex: Int, enabled: Boolean) {
        if (restoring) return
        engine.setSlotRepeat(slotIndex, enabled)
    }

    fun setLoopStart(slotIndex: Int): Boolean = !restoring && engine.setLoopStart(slotIndex)
    fun setLoopEnd(slotIndex: Int): Boolean = !restoring && engine.setLoopEnd(slotIndex)
    fun clearLoop(slotIndex: Int) { if (!restoring) engine.clearLoop(slotIndex) }

    fun toggleSlot(slotIndex: Int) {
        if (restoring) return
        val player = engine.controllerAt(slotIndex)?.player ?: return
        if ((!player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_ENDED) &&
            audioSourceManager.currentAudioSource.value >= 0 && !requestAudioFocus()) return
        engine.allowPlayback()
        engine.toggleSlot(slotIndex)
    }

    fun setSlotSpeed(slotIndex: Int, speed: PlaybackSpeed) { if (!restoring) engine.setSlotSpeed(slotIndex, speed) }

    fun retrySlot(slotIndex: Int) {
        if (restoring) return
        if (audioSourceManager.currentAudioSource.value < 0 || requestAudioFocus()) {
            engine.allowPlayback()
            engine.retrySlot(slotIndex)
        }
    }

    fun seekSlot(slotIndex: Int, positionMs: Long) {
        if (restoring) return
        if (!linkedSeek.value) engine.seekSlot(slotIndex, positionMs)
        else when (engine.seekLinked(slotIndex, positionMs)) {
            null -> warningMessage.value = QuadNotice(R.string.quad_linked_seek_not_ready)
            true -> warningMessage.value = QuadNotice(R.string.quad_linked_seek_limited)
            false -> Unit
        }
    }

    fun toggleLinkedSeek() { if (!restoring) linkedSeek.value = !linkedSeek.value }

    fun setGlobalSpeed(speed: PlaybackSpeed) {
        if (restoring) return
        engine.setGlobalSpeed(speed)
    }

    fun focusSlot(slotIndex: Int, single: Boolean = false) { if (!restoring) layoutManager.focus(slotIndex, single) }

    fun resetLayout() { if (!restoring) layoutManager.reset() }

    fun beginSwap(slotIndex: Int) {
        if (restoring) return
        pendingSwapSource.value = slotIndex
    }

    fun cancelSwap() {
        if (restoring) return
        pendingSwapSource.value = null
    }

    fun completeSwap(targetIndex: Int) {
        if (restoring) return
        val sourceIndex = pendingSwapSource.value ?: return
        importGenerations[sourceIndex]++
        importGenerations[targetIndex]++
        pendingSwapSource.value = null
        engine.swap(sourceIndex, targetIndex)
        val audioIndex = audioSourceManager.currentAudioSource.value
        val movedAudio = when (audioIndex) {
            sourceIndex -> targetIndex
            targetIndex -> sourceIndex
            else -> audioIndex
        }
        if (movedAudio != audioIndex) audioSourceManager.switchTo(movedAudio, volumeTargets, false)
    }

    fun hideRecovery() { if (!restoring) recoveryPrompt.value = recoveryPrompt.value.hide() }

    fun reopenRecovery() { if (!restoring) recoveryPrompt.value = recoveryPrompt.value.reopen() }

    fun startNewSession() {
        if (restoring) return
        recoveryPrompt.value = recoveryPrompt.value.discard()
        saveSession()
    }

    fun restoreSession() {
        val session = recoveryPrompt.value.session ?: return
        if (restoring) return
        startRestore()
        viewModelScope.launch { restoreSnapshot(session) }
    }

    fun savePreset(name: String) {
        if (!sessionReady || restoring || !recoveryPrompt.value.maySave) {
            warningMessage.value = QuadNotice(R.string.quad_preset_not_ready)
            return
        }
        val layout = layoutManager.state.value
        val snapshot = SavedQuadSession(engine.snapshot(), layout.mode, layout.focusedSlot, audioSourceManager.currentAudioSource.value)
        viewModelScope.launch {
            try {
                val message = when (presetRepository.save(name, snapshot)) {
                    SavePresetResult.SAVED -> R.string.quad_preset_saved
                    SavePresetResult.INVALID_NAME -> R.string.quad_preset_invalid_name
                    SavePresetResult.DUPLICATE_NAME -> R.string.quad_preset_duplicate
                    SavePresetResult.EMPTY_SESSION -> R.string.quad_preset_empty
                }
                warningMessage.value = QuadNotice(message, isError = message != R.string.quad_preset_saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { warningMessage.value = QuadNotice(R.string.quad_preset_error) }
        }
    }

    fun deletePreset(id: String) {
        if (restoring) return
        viewModelScope.launch {
            try { presetRepository.delete(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { warningMessage.value = QuadNotice(R.string.quad_preset_error) }
        }
    }

    fun restorePreset(id: String) {
        if (!sessionReady || restoring) return
        startRestore()
        viewModelScope.launch {
            val session = try { presetRepository.read(id) }
            catch (cancelled: CancellationException) { finishRestore(); throw cancelled }
            catch (_: Exception) { null }
            if (session == null) {
                warningMessage.value = QuadNotice(R.string.quad_preset_error)
                finishRestore()
            } else restoreSnapshot(session)
        }
    }

    private fun startRestore() {
        restoring = true
        restoreBusy.value = true
        importGenerations.indices.forEach { importGenerations[it]++ }
        engine.cancelPendingLoads()
        recoveryPrompt.value = recoveryPrompt.value.hide().copy(busy = true)
    }

    private fun finishRestore() {
        restoring = false
        restoreBusy.value = false
        recoveryPrompt.value = recoveryPrompt.value.copy(busy = false)
    }

    private suspend fun restoreSnapshot(session: SavedQuadSession) {
            var failed = false
            try {
                val resolved = session.slots.map { slot ->
                    if (slot == null) null else try {
                        val uri = Uri.parse(slot.uri)
                        // Import can re-establish a MediaStore grant from a persisted document URI.
                        videoRepository.importVideo(uri)?.takeIf { canReadMedia(context, it.uri) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
                if (session.slots.indices.any { session.slots[it] != null && resolved[it] == null }) failed = true
                if (resolved.none { it != null }) {
                    warningMessage.value = QuadNotice(R.string.quad_session_some_missing)
                    return
                }
                audioSourceManager.muteAll(volumeTargets)
                (0..3).forEach(engine::remove)
                session.slots.forEachIndexed { index, slot ->
                    val video = resolved[index]
                    if (slot != null && video != null) {
                        try {
                            if (!loadAndInspect(index, video)) { failed = true; return@forEachIndexed }
                            engine.seekSlot(index, slot.positionMs)
                            engine.setSlotSpeed(index, PlaybackSpeed.fromValue(slot.speed))
                            engine.setSlotPlaying(index, slot.playing)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) { failed = true }
                    }
                }
                when (session.layout) {
                    LayoutMode.EQUAL_GRID -> layoutManager.reset()
                    LayoutMode.FOCUS_GRID -> layoutManager.focus(session.focusedSlot)
                    LayoutMode.SINGLE -> layoutManager.focus(session.focusedSlot, single = true)
                }
                val loaded = (0..3).filter(engine::hasController)
                val chosenAudio = recoveredAudioSource(session.audioSource, loaded)
                if (chosenAudio >= 0 && engine.hasPlayIntent() && audioFocus.active && !requestAudioFocus()) engine.pauseAll()
                if (chosenAudio >= 0) audioSourceManager.switchTo(chosenAudio, volumeTargets, false)
                else audioSourceManager.muteAll(volumeTargets)
                if (failed || (session.audioSource >= 0 && chosenAudio != session.audioSource)) {
                    warningMessage.value = QuadNotice(R.string.quad_session_some_missing)
                }
                if (!failed) recoveryPrompt.value = recoveryPrompt.value.discard()
            } finally {
                finishRestore()
            }
    }

    fun saveSession() {
        if (!sessionReady || restoring || released || !recoveryPrompt.value.maySave) return
        val layout = layoutManager.state.value
        sessionRepository.save(SavedQuadSession(engine.snapshot(), layout.mode, layout.focusedSlot, audioSourceManager.currentAudioSource.value))
    }

    fun clearWarning() {
        if (warningMessage.value != null) warningMessage.value = null
        else if (capabilityWarning.value != null) capabilityWarning.value = null
        else performanceWarnings.value = performanceWarnings.value.drop(1)
    }

    override fun onCleared() {
        saveSession()
        released = true
        audioFocus.stop()
        performanceMonitor.stop()
        engine.release()
        super.onCleared()
    }
}
