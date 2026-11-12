package com.quadplayer.feature.quadplayer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.R
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.data.QuadSessionRepository
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

data class QuadPlayerUiState(
    val slots: List<QuadSlotUiState> = List(4) { QuadSlotUiState(index = it) },
    val layoutMode: LayoutMode = LayoutMode.EQUAL_GRID,
    val focusedSlot: Int = 0,
    val settings: AppSettings = AppSettings(),
    val warningMessage: String? = null,
    val pendingSwapSource: Int? = null,
    val offerRecovery: Boolean = false,
    val recoveryAvailable: Boolean = false,
)

@HiltViewModel
class QuadPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: QuadSessionRepository,
    private val capabilityChecker: DeviceCapabilityChecker,
    private val performanceMonitor: PerformanceMonitor,
) : ViewModel() {
    private val engine = QuadPlaybackEngine(context)
    private val audioFocus = QuadAudioFocus(context) { engine.interruptPlayback() }
    private val audioSourceManager = AudioSourceManager(viewModelScope)
    private val layoutManager = QuadLayoutManager()
    private val warningMessage = MutableStateFlow<String?>(null)
    private val performanceWarnings = MutableStateFlow<List<PerformanceWarning>>(emptyList())
    private val pendingSwapSource = MutableStateFlow<Int?>(null)
    private val recoveryPrompt = MutableStateFlow(RecoveryPrompt())
    private var sessionReady = false
    private var restoring = false
    private var released = false
    private val volumeTargets = List(4) { QuadVolumeTarget(engine, it) }

    private val baseUiState = combine(
        engine.slotStates(audioSourceManager.currentAudioSource),
        layoutManager.state,
        settingsRepository.settings,
    ) { slots, layout, settings ->
        QuadPlayerUiState(
            slots = slots,
            layoutMode = layout.mode,
            focusedSlot = layout.focusedSlot,
            settings = settings,
        )
    }

    val uiState: StateFlow<QuadPlayerUiState> = combine(
        baseUiState,
        warningMessage,
        performanceWarnings,
        pendingSwapSource,
        recoveryPrompt,
    ) { baseState, warning, queuedWarnings, pendingSwap, recovery ->
        baseState.copy(
            warningMessage = warning ?: when (queuedWarnings.firstOrNull()) {
                PerformanceWarning.LowMemory -> context.getString(R.string.quad_warning_low_memory)
                PerformanceWarning.ThermalThrottle -> context.getString(R.string.quad_warning_thermal)
                null -> null
            },
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

        val capability = capabilityChecker.check()
        if (capability.warning != null) {
            warningMessage.value = context.getString(
                R.string.quad_warning_capability,
                capability.totalRamMb,
                capability.maxConcurrentDecoders,
            )
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
                warningMessage.value = context.getString(R.string.quad_session_read_failed)
            } finally { sessionReady = true }
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
                    val fallback = (0..3).firstOrNull { index ->
                        index != source && engine.controllerAt(index)?.let { it.uiState.value.errorMessage == null } == true
                    }
                    if (fallback != null) audioSourceManager.switchTo(fallback, volumeTargets, false)
                    else audioSourceManager.muteAll(volumeTargets)
                    warningMessage.value = context.getString(R.string.quad_audio_source_failed)
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
        warningMessage.value = context.getString(R.string.quad_audio_focus_unavailable)
        return false
    }

    fun addVideo(slotIndex: Int, uri: Uri) {
        viewModelScope.launch {
            try {
                val video = videoRepository.importVideo(uri)
                if (video == null) {
                    warningMessage.value = context.getString(R.string.quad_import_failed, slotIndex + 1)
                    return@launch
                }
                if (audioFocus.active && !requestAudioFocus()) return@launch
                engine.allowPlayback()
                loadAndInspect(slotIndex, video)
                val settings = uiState.value.settings
                if (audioSourceManager.currentAudioSource.value == -1) {
                    audioSourceManager.switchTo(slotIndex, volumeTargets, settings.crossfadeAudioSwitching)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                warningMessage.value = context.getString(R.string.quad_import_failed, slotIndex + 1)
            }
        }
    }

    private suspend fun loadAndInspect(slotIndex: Int, video: com.quadplayer.core.model.VideoFile) {
        engine.load(slotIndex, video)
        viewModelScope.launch {
            val warning = withContext(Dispatchers.IO) { capabilityChecker.inspectVideo(video.uri) }
            if (engine.snapshot().getOrNull(slotIndex)?.uri == video.uriString) {
                when (warning) {
                    VideoCapabilityWarning.NoSupportedDecoder -> warningMessage.value = context.getString(R.string.quad_warning_format)
                    VideoCapabilityWarning.SoftwareOnly -> warningMessage.value = context.getString(R.string.quad_warning_software)
                    null -> Unit
                }
            }
        }
    }

    fun removeSlot(slotIndex: Int) {
        viewModelScope.launch {
            engine.remove(slotIndex)
            if (audioSourceManager.currentAudioSource.value == slotIndex) {
                audioSourceManager.muteAll(volumeTargets)
            }
        }
    }

    fun toggleAudioSource(slotIndex: Int) {
        if (engine.hasPlayIntent() && !requestAudioFocus()) return
        val settings = uiState.value.settings
        audioSourceManager.switchTo(slotIndex, volumeTargets, settings.crossfadeAudioSwitching)
    }

    fun muteAll() {
        audioSourceManager.muteAll(volumeTargets)
        audioFocus.abandon()
    }

    fun togglePlayAll() {
        if (!engine.hasPlayIntent() && audioSourceManager.currentAudioSource.value >= 0 && !requestAudioFocus()) return
        engine.allowPlayback()
        engine.togglePlayAll()
    }

    fun syncToAudioSource() {
        val sourceIndex = audioSourceManager.currentAudioSource.value
        if (sourceIndex !in 0..3) return
        val position = engine.currentPosition(sourceIndex) ?: return
        engine.syncToPosition(position)
    }

    fun toggleSlot(slotIndex: Int) {
        val player = engine.controllerAt(slotIndex)?.player ?: return
        if ((!player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_ENDED) &&
            audioSourceManager.currentAudioSource.value >= 0 && !requestAudioFocus()) return
        engine.allowPlayback()
        engine.toggleSlot(slotIndex)
    }

    fun setSlotSpeed(slotIndex: Int, speed: PlaybackSpeed) = engine.setSlotSpeed(slotIndex, speed)

    fun retrySlot(slotIndex: Int) {
        if (audioSourceManager.currentAudioSource.value < 0 || requestAudioFocus()) {
            engine.allowPlayback()
            engine.controllerAt(slotIndex)?.retry()
        }
    }

    fun seekSlot(slotIndex: Int, positionMs: Long) {
        engine.seekSlot(slotIndex, positionMs)
    }

    fun setGlobalSpeed(speed: PlaybackSpeed) {
        engine.setGlobalSpeed(speed)
    }

    fun focusSlot(slotIndex: Int, single: Boolean = false) = layoutManager.focus(slotIndex, single)

    fun resetLayout() = layoutManager.reset()

    fun beginSwap(slotIndex: Int) {
        pendingSwapSource.value = slotIndex
    }

    fun cancelSwap() {
        pendingSwapSource.value = null
    }

    fun completeSwap(targetIndex: Int) {
        val sourceIndex = pendingSwapSource.value ?: return
        pendingSwapSource.value = null
        viewModelScope.launch {
            engine.swap(sourceIndex, targetIndex)
            val audioIndex = audioSourceManager.currentAudioSource.value
            val movedAudio = when (audioIndex) {
                sourceIndex -> targetIndex
                targetIndex -> sourceIndex
                else -> audioIndex
            }
            if (movedAudio != audioIndex) audioSourceManager.switchTo(movedAudio, volumeTargets, false)
        }
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
        recoveryPrompt.value = recoveryPrompt.value.hide().copy(busy = true)
        restoring = true
        viewModelScope.launch {
            var failed = false
            try {
                audioSourceManager.muteAll(volumeTargets)
                (0..3).forEach(engine::remove)
                session.slots.forEachIndexed { index, slot ->
                    if (slot != null) {
                        try {
                            val uri = Uri.parse(slot.uri)
                            if (!canReadMedia(context, uri)) { failed = true; return@forEachIndexed }
                            val video = videoRepository.importVideo(uri)
                            if (video == null) { failed = true; return@forEachIndexed }
                            loadAndInspect(index, video)
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
                    warningMessage.value = context.getString(R.string.quad_session_some_missing)
                }
                recoveryPrompt.value = recoveryPrompt.value.discard()
            } finally {
                restoring = false
                recoveryPrompt.value = recoveryPrompt.value.copy(busy = false)
            }
        }
    }

    fun saveSession() {
        if (!sessionReady || restoring || released || !recoveryPrompt.value.maySave) return
        val layout = layoutManager.state.value
        sessionRepository.save(SavedQuadSession(engine.snapshot(), layout.mode, layout.focusedSlot, audioSourceManager.currentAudioSource.value))
    }

    fun clearWarning() {
        if (warningMessage.value != null) warningMessage.value = null
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
