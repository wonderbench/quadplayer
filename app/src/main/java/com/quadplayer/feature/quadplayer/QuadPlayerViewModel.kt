package com.quadplayer.feature.quadplayer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.R
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.data.VideoRepository
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.LayoutMode
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.util.DeviceCapabilityChecker
import com.quadplayer.core.util.PerformanceMonitor
import com.quadplayer.core.util.PerformanceWarning
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

data class QuadPlayerUiState(
    val slots: List<QuadSlotUiState> = List(4) { QuadSlotUiState(index = it) },
    val layoutMode: LayoutMode = LayoutMode.EQUAL_GRID,
    val settings: AppSettings = AppSettings(),
    val warningMessage: String? = null,
    val pendingSwapSource: Int? = null,
)

@HiltViewModel
class QuadPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val settingsRepository: SettingsRepository,
    private val capabilityChecker: DeviceCapabilityChecker,
    private val performanceMonitor: PerformanceMonitor,
) : ViewModel() {
    private val engine = QuadPlaybackEngine(context)
    private val audioSourceManager = AudioSourceManager(viewModelScope)
    private val layoutManager = QuadLayoutManager()
    private val warningMessage = MutableStateFlow<String?>(null)
    private val pendingSwapSource = MutableStateFlow<Int?>(null)
    private val volumeTargets = List(4) { QuadVolumeTarget(engine.controllerAt(it)) }

    private val baseUiState = combine(
        engine.slotStates(audioSourceManager.currentAudioSource),
        layoutManager.layoutMode,
        settingsRepository.settings,
    ) { slots, layoutMode, settings ->
        QuadPlayerUiState(
            slots = slots,
            layoutMode = layoutMode,
            settings = settings,
        )
    }

    val uiState: StateFlow<QuadPlayerUiState> = combine(
        baseUiState,
        warningMessage,
        pendingSwapSource,
    ) { baseState, warning, pendingSwap ->
        baseState.copy(
            warningMessage = warning,
            pendingSwapSource = pendingSwap,
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
    }

    fun controllerAt(index: Int): PlayerController = engine.controllerAt(index)

    fun addVideo(slotIndex: Int, uri: Uri) {
        viewModelScope.launch {
            val video = videoRepository.importVideo(uri) ?: return@launch
            engine.load(slotIndex, video)
            val settings = uiState.value.settings
            if (audioSourceManager.currentAudioSource.value == -1) {
                audioSourceManager.switchTo(slotIndex, volumeTargets, settings.crossfadeAudioSwitching)
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
        val sourceState = uiState.value.slots[sourceIndex]
        engine.syncToPosition(sourceState.positionMs)
    }

    fun seekSlot(slotIndex: Int, positionMs: Long) {
        engine.seekSlot(slotIndex, positionMs)
    }

    fun setGlobalSpeed(speed: PlaybackSpeed) {
        engine.setGlobalSpeed(speed)
    }

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

    fun clearWarning() {
        warningMessage.value = null
    }

    override fun onCleared() {
        performanceMonitor.stop()
        engine.release()
        super.onCleared()
    }
}
