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
    private val audioSourceManager = AudioSourceManager(viewModelScope)
    private val layoutManager = QuadLayoutManager()
    private val warningMessage = MutableStateFlow<String?>(null)
    private val pendingSwapSource = MutableStateFlow<Int?>(null)
    private val pendingRecovery = MutableStateFlow<SavedQuadSession?>(null)
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
        pendingSwapSource,
        pendingRecovery,
    ) { baseState, warning, pendingSwap, recovery ->
        baseState.copy(
            warningMessage = warning,
            pendingSwapSource = pendingSwap,
            offerRecovery = recovery != null,
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
                warningMessage.value = when (warning) {
                    PerformanceWarning.LowMemory -> context.getString(R.string.quad_warning_low_memory)
                    PerformanceWarning.ThermalThrottle -> context.getString(R.string.quad_warning_thermal)
                }
            }
        }

        audioSourceManager.muteAll(volumeTargets)
        layoutManager.reset()
        viewModelScope.launch {
            try {
                val previous = sessionRepository.read()
                if (engine.snapshot().all { it == null } && previous?.slots?.any { it != null } == true) {
                    pendingRecovery.value = previous
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
    }

    fun controllerAt(index: Int): PlayerController? = engine.controllerAt(index)

    fun addVideo(slotIndex: Int, uri: Uri) {
        viewModelScope.launch {
            val video = videoRepository.importVideo(uri) ?: return@launch
            engine.load(slotIndex, video)
            val settings = uiState.value.settings
            if (audioSourceManager.currentAudioSource.value == -1) {
                audioSourceManager.switchTo(slotIndex, volumeTargets, settings.crossfadeAudioSwitching)
            }
            val activeCount = engine.snapshot().count { it != null }
            val warning = withContext(Dispatchers.IO) { capabilityChecker.inspectVideo(video.uri, activeCount) }
            if (engine.snapshot().getOrNull(slotIndex)?.uri == video.uriString) {
                when (warning) {
                    VideoCapabilityWarning.NoSupportedDecoder -> warningMessage.value = context.getString(R.string.quad_warning_format)
                    VideoCapabilityWarning.SoftwareOnly -> warningMessage.value = context.getString(R.string.quad_warning_software)
                    is VideoCapabilityWarning.ConcurrentLimit -> warningMessage.value = context.getString(R.string.quad_warning_concurrency, warning.count, warning.reportedLimit)
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
        val settings = uiState.value.settings
        audioSourceManager.switchTo(slotIndex, volumeTargets, settings.crossfadeAudioSwitching)
    }

    fun muteAll() {
        audioSourceManager.muteAll(volumeTargets)
    }

    fun togglePlayAll() {
        engine.togglePlayAll()
    }

    fun syncToAudioSource() {
        val sourceIndex = audioSourceManager.currentAudioSource.value
        if (sourceIndex !in 0..3) return
        val position = engine.currentPosition(sourceIndex) ?: return
        engine.syncToPosition(position)
    }

    fun toggleSlot(slotIndex: Int) = engine.toggleSlot(slotIndex)

    fun setSlotSpeed(slotIndex: Int, speed: PlaybackSpeed) = engine.setSlotSpeed(slotIndex, speed)

    fun retrySlot(slotIndex: Int) = engine.controllerAt(slotIndex)?.retry()

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

    fun dismissRecovery() { pendingRecovery.value = null }

    fun restoreSession() {
        val session = pendingRecovery.value ?: return
        pendingRecovery.value = null
        restoring = true
        viewModelScope.launch {
            var failed = false
            try {
                session.slots.forEachIndexed { index, slot ->
                    if (slot != null) {
                        try {
                            val video = videoRepository.importVideo(Uri.parse(slot.uri))
                            if (video == null) { failed = true; return@forEachIndexed }
                            engine.load(index, video)
                            engine.seekSlot(index, slot.positionMs)
                            engine.setSlotSpeed(index, PlaybackSpeed.fromValue(slot.speed))
                            if (!slot.playing) engine.controllerAt(index)?.pause()
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
                if (session.audioSource in 0..3 && engine.hasController(session.audioSource)) {
                    audioSourceManager.switchTo(session.audioSource, volumeTargets, false)
                } else audioSourceManager.muteAll(volumeTargets)
                if (failed) warningMessage.value = context.getString(R.string.quad_session_some_missing)
            } finally { restoring = false }
        }
    }

    fun saveSession() {
        if (!sessionReady || restoring || released || pendingRecovery.value != null) return
        val layout = layoutManager.state.value
        sessionRepository.save(SavedQuadSession(engine.snapshot(), layout.mode, layout.focusedSlot, audioSourceManager.currentAudioSource.value))
    }

    fun clearWarning() {
        warningMessage.value = null
    }

    override fun onCleared() {
        saveSession()
        released = true
        performanceMonitor.stop()
        engine.release()
        super.onCleared()
    }
}
