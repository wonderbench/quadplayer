@file:kotlin.OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.quadplayer.feature.quadplayer

import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import com.quadplayer.ui.theme.DepthChip
import com.quadplayer.ui.theme.DepthChoiceChip
import com.quadplayer.core.ui.applySubtitleStyle
import com.quadplayer.core.model.SubtitleStyle
import com.quadplayer.feature.player.playbackFailureShortText
import androidx.compose.material3.Icon
import com.quadplayer.ui.theme.RoundIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.quadplayer.core.util.formatDuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.quadplayer.R
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.LayoutMode
import com.quadplayer.core.data.QuadPreset
import com.quadplayer.core.ui.ImmersiveFullscreen
import com.quadplayer.core.ui.LockScreenOrientation
import kotlinx.coroutines.delay

@Composable
fun QuadPlayerScreen(
    viewModel: QuadPlayerViewModel,
    onBack: () -> Unit,
) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    ImmersiveFullscreen()

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler(enabled = state.layoutMode != LayoutMode.EQUAL_GRID) { viewModel.resetLayout() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenStarted()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.onScreenStopped() }
    }
    var pendingSlot by remember { mutableIntStateOf(-1) }
    var menuSlot by remember { mutableStateOf<Int?>(null) }
    var showPresets by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var presetToRestore by remember { mutableStateOf<QuadPreset?>(null) }
    var presetToDelete by remember { mutableStateOf<QuadPreset?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var controlsInteractionVersion by remember { mutableIntStateOf(0) }
    var progressVisibleSlot by remember { mutableIntStateOf(-1) }
    var progressInteractionVersion by remember { mutableIntStateOf(0) }
    var bottomOverlayHeight by remember { mutableIntStateOf(0) }
    val progressBottomInset = if (controlsVisible) with(LocalDensity.current) { bottomOverlayHeight.toDp() } else 0.dp

    DisposableEffect(lifecycleOwner, progressVisibleSlot) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.setProgressVisibleSlot(progressVisibleSlot)
            if (event == Lifecycle.Event.ON_STOP) viewModel.setProgressVisibleSlot(-1)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.setProgressVisibleSlot(-1) }
    }

    val hasPlayingVideo = state.slots.any { it.isPlaying }

    LaunchedEffect(
        controlsVisible,
        hasPlayingVideo,
        state.pendingSwapSource,
        menuSlot,
        controlsInteractionVersion,
    ) {
        if (controlsVisible && hasPlayingVideo && state.pendingSwapSource == null && menuSlot == null) {
            delay(5000)
            controlsVisible = false
        }
    }

    LaunchedEffect(progressVisibleSlot, menuSlot, progressInteractionVersion) {
        if (progressVisibleSlot >= 0 && menuSlot == null) {
            delay(5000)
            progressVisibleSlot = -1
        }
    }

    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        val targetSlot = pendingSlot
        pendingSlot = -1
        if (uri != null && targetSlot >= 0) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.addVideo(targetSlot, uri)
            progressVisibleSlot = targetSlot
            progressInteractionVersion += 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        QuadGrid(
            modifier = Modifier.fillMaxSize(),
            state = state,
            viewModel = viewModel,
            progressBottomInset = progressBottomInset,
            onAddVideo = { slotIndex ->
                pendingSlot = slotIndex
                picker.launch(arrayOf("video/*"))
            },
            onOpenMenu = { slotIndex ->
                menuSlot = slotIndex
                progressVisibleSlot = -1
            },
            onSwitchAudio = { slotIndex ->
                viewModel.toggleAudioSource(slotIndex)
                progressVisibleSlot = slotIndex
                progressInteractionVersion += 1
            },
            onShowProgress = { slotIndex ->
                progressVisibleSlot = slotIndex
                progressInteractionVersion += 1
            },
            onSeek = { slotIndex, positionMs ->
                viewModel.seekSlot(slotIndex, positionMs)
                progressVisibleSlot = slotIndex
                progressInteractionVersion += 1
            },
            visibleProgressSlot = progressVisibleSlot,
        )

        TopOverlay(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp),
            warningMessage = state.warningMessage,
            pendingSwapSource = state.pendingSwapSource,
            controlsVisible = controlsVisible,
            onExit = onBack,
            onHideControls = { controlsVisible = false },
            onDismissWarning = viewModel::clearWarning,
            onShowControls = {
                controlsVisible = true
                controlsInteractionVersion += 1
            },
        )

        if (state.recoveryAvailable) {
            Surface(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), tonalElevation = 6.dp) {
                TextButton(onClick = viewModel::reopenRecovery) { Text(stringResource(R.string.quad_session_reopen)) }
            }
        }

        if (state.layoutMode != LayoutMode.EQUAL_GRID) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = if (controlsVisible) 76.dp else 8.dp),
                tonalElevation = 6.dp,
            ) {
                TextButton(onClick = viewModel::resetLayout) { Text(stringResource(R.string.quad_restore_grid)) }
            }
        }

        if (controlsVisible) {
            BottomOverlay(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomOverlayHeight = it.height }
                    .padding(8.dp),
                slots = state.slots,
                linkedSeek = state.linkedSeek,
                onPresets = { showPresets = true },
                presetsReady = state.presetsReady,
                onInteraction = { controlsInteractionVersion += 1 },
                onTogglePlayAll = viewModel::togglePlayAll,
                onSynchronize = viewModel::syncToAudioSource,
                onToggleLinkedSeek = viewModel::toggleLinkedSeek,
                onMuteAll = viewModel::muteAll,
                onSetGlobalSpeed = viewModel::setGlobalSpeed,
            )
        }
        if (state.restoring) {
            Surface(modifier = Modifier.fillMaxSize().clickable { }, color = Color.Black.copy(alpha = 0.7f)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.quad_preset_restoring), color = Color.White)
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                }
            }
        }
    }

    BackHandler(enabled = state.restoring) { onBack() }

    if (showPresets) {
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Dialog(onDismissRequest = { showPresets = false }) {
            Surface(modifier = Modifier.imePadding().fillMaxWidth(), shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp) {
                Column(Modifier.padding(if (keyboardVisible) 8.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!keyboardVisible) Text(stringResource(R.string.quad_presets_title), style = MaterialTheme.typography.headlineSmall)
                    TextField(modifier = Modifier.fillMaxWidth(), value = presetName, onValueChange = { if (it.length <= 80) presetName = it },
                        label = { Text(stringResource(R.string.quad_preset_name)) }, singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (presetName.isNotBlank()) { viewModel.savePreset(presetName); presetName = ""; showPresets = false }
                        }),
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    if (!keyboardVisible) {
                        if (state.presets.isEmpty()) Text(stringResource(R.string.quad_preset_none))
                        else LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                            items(state.presets.size, key = { state.presets[it].id }) { index ->
                                val preset = state.presets[index]
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(modifier = Modifier.weight(1f), onClick = { presetToRestore = preset }) { Text(preset.name) }
                                    TextButton(onClick = { presetToDelete = preset }) { Text(stringResource(R.string.quad_preset_delete)) }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPresets = false }) { Text(stringResource(R.string.common_done)) }
                        TextButton(enabled = presetName.isNotBlank(), onClick = { viewModel.savePreset(presetName); presetName = ""; showPresets = false }) { Text(stringResource(R.string.quad_preset_save)) }
                    }
                }
            }
        }
    }

    presetToRestore?.let { preset ->
        AlertDialog(onDismissRequest = { presetToRestore = null }, title = { Text(stringResource(R.string.quad_preset_load_title)) },
            text = { Text(stringResource(R.string.quad_preset_load_confirm, preset.name)) },
            confirmButton = { TextButton(onClick = { viewModel.restorePreset(preset.id); presetToRestore = null; showPresets = false }) { Text(stringResource(R.string.quad_session_restore)) } },
            dismissButton = { TextButton(onClick = { presetToRestore = null }) { Text(stringResource(R.string.common_cancel)) } })
    }
    presetToDelete?.let { preset ->
        AlertDialog(onDismissRequest = { presetToDelete = null }, title = { Text(stringResource(R.string.quad_preset_delete_title)) },
            text = { Text(stringResource(R.string.quad_preset_delete_confirm, preset.name)) },
            confirmButton = { TextButton(onClick = { viewModel.deletePreset(preset.id); presetToDelete = null }) { Text(stringResource(R.string.quad_preset_delete)) } },
            dismissButton = { TextButton(onClick = { presetToDelete = null }) { Text(stringResource(R.string.common_cancel)) } })
    }

    if (state.offerRecovery) {
        AlertDialog(
            onDismissRequest = viewModel::hideRecovery,
            title = { Text(stringResource(R.string.quad_session_title)) },
            text = { Text(stringResource(R.string.quad_session_message)) },
            confirmButton = { TextButton(onClick = viewModel::restoreSession) { Text(stringResource(R.string.quad_session_restore)) } },
            dismissButton = { TextButton(onClick = viewModel::startNewSession) { Text(stringResource(R.string.quad_session_new)) } },
        )
    }

    menuSlot?.let { slotIndex ->
        val slot = state.slots[slotIndex]
        SlotMenuDialog(
            slot = slot,
            pendingSwapSource = state.pendingSwapSource,
            onDismiss = { menuSlot = null },
            onRemove = {
                viewModel.removeSlot(slotIndex)
                menuSlot = null
            },
            onSwap = {
                when (state.pendingSwapSource) {
                    null -> viewModel.beginSwap(slotIndex)
                    slotIndex -> viewModel.cancelSwap()
                    else -> viewModel.completeSwap(slotIndex)
                }
                menuSlot = null
            },
            onFocus = { viewModel.focusSlot(slotIndex); menuSlot = null },
            onEnlarge = { viewModel.focusSlot(slotIndex, single = true); menuSlot = null },
            onTogglePlayback = { viewModel.toggleSlot(slotIndex); menuSlot = null },
            onToggleRepeat = { viewModel.setSlotRepeat(slotIndex, !slot.repeatOne); menuSlot = null },
            onSetLoopStart = { viewModel.setLoopStart(slotIndex) },
            onSetLoopEnd = { viewModel.setLoopEnd(slotIndex) },
            onClearLoop = { viewModel.clearLoop(slotIndex); menuSlot = null },
            onSetSpeed = { speed -> viewModel.setSlotSpeed(slotIndex, speed); menuSlot = null },
            onSwitchAudio = { viewModel.toggleAudioSource(slotIndex); menuSlot = null },
            onReplace = {
                pendingSlot = slotIndex
                menuSlot = null
                picker.launch(arrayOf("video/*"))
            },
        )
    }
}

@Composable
private fun TopOverlay(
    modifier: Modifier,
    warningMessage: QuadNotice?,
    pendingSwapSource: Int?,
    controlsVisible: Boolean,
    onExit: () -> Unit,
    onHideControls: () -> Unit,
    onDismissWarning: () -> Unit,
    onShowControls: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (controlsVisible) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onExit) { Text(stringResource(R.string.common_back)) }
                    Text(
                        text = stringResource(R.string.quad_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onHideControls) { Text(stringResource(R.string.quad_hide_controls)) }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                RoundIconButton(onClick = onShowControls) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.quad_show_controls),
                        )
                }
            }
        }

        warningMessage?.let { warning ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
                color = (if (warning.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.94f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = quadNoticeText(warning),
                        modifier = Modifier.weight(1f),
                        color = if (warning.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onDismissWarning) {
                        Text(stringResource(R.string.quad_dismiss))
                    }
                }
            }
        }

        pendingSwapSource?.let { sourceIndex ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
            ) {
                Text(
                    text = stringResource(R.string.quad_swap_hint, sourceIndex + 1),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun quadNoticeText(notice: QuadNotice): String =
    stringResource(notice.resourceId, *notice.arguments.toTypedArray())

@Composable
internal fun BottomOverlay(
    modifier: Modifier,
    slots: List<QuadSlotUiState>,
    linkedSeek: Boolean,
    onPresets: () -> Unit,
    presetsReady: Boolean,
    onInteraction: () -> Unit,
    onTogglePlayAll: () -> Unit,
    onSynchronize: () -> Unit,
    onToggleLinkedSeek: () -> Unit,
    onMuteAll: () -> Unit,
    onSetGlobalSpeed: (PlaybackSpeed) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                DepthChip(enabled = presetsReady, onClick = { onInteraction(); onPresets() }, label = { Text(stringResource(R.string.quad_presets_title)) })
                DepthChip(
                    onClick = {
                        onInteraction()
                        onTogglePlayAll()
                    },
                    label = { Text(stringResource(R.string.quad_play_pause_all)) },
                )
                DepthChip(
                    onClick = {
                        onInteraction()
                        onSynchronize()
                    },
                    label = { Text(stringResource(R.string.quad_sync_to_audio)) },
                )
                DepthChoiceChip(
                    selected = linkedSeek,
                    onClick = { onInteraction(); onToggleLinkedSeek() },
                    label = { Text(stringResource(R.string.quad_linked_seek)) },
                )
                DepthChip(
                    onClick = {
                        onInteraction()
                        onMuteAll()
                    },
                    label = { Text(stringResource(R.string.quad_mute_all)) },
                )
                Text(stringResource(R.string.quad_speed_title), style = MaterialTheme.typography.labelLarge)
                val loaded = slots.filter { it.video != null }
                PlaybackSpeed.entries.forEach { speed ->
                    DepthChoiceChip(
                        selected = loaded.isNotEmpty() && loaded.all { it.playbackSpeed == speed },
                        onClick = {
                            onInteraction()
                            onSetGlobalSpeed(speed)
                        },
                        label = { Text(speed.label) },
                    )
                }
        }
    }
}

@Composable
private fun QuadGrid(
    modifier: Modifier,
    state: QuadPlayerUiState,
    viewModel: QuadPlayerViewModel,
    progressBottomInset: Dp,
    onAddVideo: (Int) -> Unit,
    onOpenMenu: (Int) -> Unit,
    onSwitchAudio: (Int) -> Unit,
    onShowProgress: (Int) -> Unit,
    onSeek: (Int, Long) -> Unit,
    visibleProgressSlot: Int,
) {
    @Composable
    fun Slot(index: Int, slotModifier: Modifier, audioTopInset: Dp = 4.dp, bottomInset: Dp = 0.dp) {
        QuadSlotContent(slotModifier, state.slots[index], state.settings.subtitleStyle, viewModel.controllerAt(index)?.player, viewModel::retrySlot, onAddVideo, onOpenMenu,
            onSwitchAudio, onShowProgress, onSeek, visibleProgressSlot == index, audioTopInset, bottomInset)
    }
    when (state.layoutMode) {
        LayoutMode.EQUAL_GRID -> Column(modifier = modifier) {
            Row(Modifier.weight(1f)) {
                Slot(0, Modifier.weight(1f)); Slot(1, Modifier.weight(1f))
            }
            Row(Modifier.weight(1f)) {
                Slot(2, Modifier.weight(1f), bottomInset = progressBottomInset)
                Slot(3, Modifier.weight(1f), bottomInset = progressBottomInset)
            }
        }
        LayoutMode.FOCUS_GRID -> Row(modifier = modifier) {
            Slot(state.focusedSlot, Modifier.weight(3f).fillMaxHeight(), bottomInset = progressBottomInset)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                (0..3).filter { it != state.focusedSlot }.forEachIndexed { position, index ->
                    Slot(index, Modifier.weight(1f).fillMaxWidth(), bottomInset = if (position == 2) progressBottomInset else 0.dp)
                }
            }
        }
        LayoutMode.SINGLE -> Box(modifier = modifier) {
            Slot(state.focusedSlot, Modifier.fillMaxSize(), audioTopInset = 60.dp, bottomInset = progressBottomInset)
        }
    }
}

@Composable
private fun QuadSlotContent(
    modifier: Modifier,
    slot: QuadSlotUiState,
    subtitleStyle: SubtitleStyle,
    playbackPlayer: androidx.media3.common.Player?,
    onRetry: (Int) -> Unit,
    onAddVideo: (Int) -> Unit,
    onOpenMenu: (Int) -> Unit,
    onSwitchAudio: (Int) -> Unit,
    onShowProgress: (Int) -> Unit,
    onSeek: (Int, Long) -> Unit,
    progressVisible: Boolean,
    audioTopInset: Dp,
    progressBottomInset: Dp,
) {
    var isSeeking by remember(slot.index) { mutableStateOf(false) }
    var sliderPosition by remember(slot.index) {
        mutableLongStateOf(0L)
    }
    LaunchedEffect(slot.positionMs, slot.durationMs, isSeeking) {
        if (!isSeeking) {
            sliderPosition = slot.positionMs.coerceIn(0L, slot.durationMs.coerceAtLeast(0L))
        }
    }
    val audioAction = stringResource(R.string.quad_select_audio)
    val menuAction = stringResource(R.string.quad_slot_actions)
    val progressLabel = stringResource(R.string.quad_slot_progress, slot.index + 1)
    val interaction = quadSlotInteraction(
        empty = slot.video == null, audioLabel = audioAction, menuLabel = menuAction,
        onAdd = { onAddVideo(slot.index) }, onProgress = { onShowProgress(slot.index) },
        onAudio = { onSwitchAudio(slot.index) }, onMenu = { onOpenMenu(slot.index) },
    )

    Box(
        modifier = modifier
            .background(Color.Black)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (slot.video == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.quad_tap_to_add),
                        color = Color.White,
                    )
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            player = playbackPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { playerView ->
                        playerView.player = playbackPlayer
                        playerView.keepScreenOn = slot.isPlaying
                        playerView.applySubtitleStyle(subtitleStyle)
                        playerView.setShutterBackgroundColor(android.graphics.Color.BLACK)
                        playerView.setBackgroundColor(android.graphics.Color.BLACK)
                    },
                )
                if (slot.errorMessage != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(playbackFailureShortText(slot.failure)), maxLines = 2)
                            Row {
                                TextButton(onClick = { onRetry(slot.index) }) { Text(stringResource(R.string.player_retry)) }
                                TextButton(onClick = { onAddVideo(slot.index) }) { Text(stringResource(R.string.quad_replace_video)) }
                            }
                        }
                    }
                }
                RoundIconButton(
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = audioTopInset),
                    onClick = { onSwitchAudio(slot.index) },
                ) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = stringResource(R.string.quad_select_audio),
                            tint = if (slot.isAudioSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                if (slot.isAudioSource) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    ) {
                        Text(
                            text = stringResource(R.string.quad_current_audio),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (progressVisible) {
                    Slider(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = progressBottomInset)
                            .semantics { contentDescription = progressLabel }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        value = sliderPosition.toFloat(),
                        onValueChange = {
                            isSeeking = true
                            sliderPosition = it.toLong()
                        },
                        valueRange = 0f..slot.durationMs.coerceAtLeast(1L).toFloat(),
                        onValueChangeFinished = {
                            isSeeking = false
                            onSeek(slot.index, sliderPosition)
                        },
                    )
                }
            }
        }
    }
}

internal fun quadSlotInteraction(
    empty: Boolean,
    audioLabel: String,
    menuLabel: String,
    onAdd: () -> Unit,
    onProgress: () -> Unit,
    onAudio: () -> Unit,
    onMenu: () -> Unit,
): Modifier = if (empty) {
    Modifier.clickable(onClick = onAdd)
} else {
    Modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction(audioLabel) { onAudio(); true },
            CustomAccessibilityAction(menuLabel) { onMenu(); true },
        )
    }.combinedClickable(onClick = onProgress, onDoubleClick = onAudio, onLongClick = onMenu)
}

@Composable
private fun SlotMenuDialog(
    slot: QuadSlotUiState,
    pendingSwapSource: Int?,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onSwap: () -> Unit,
    onReplace: () -> Unit,
    onFocus: () -> Unit,
    onEnlarge: () -> Unit,
    onTogglePlayback: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSetLoopStart: () -> Boolean,
    onSetLoopEnd: () -> Boolean,
    onClearLoop: () -> Unit,
    onSetSpeed: (PlaybackSpeed) -> Unit,
    onSwitchAudio: () -> Unit,
) {
    var loopError by remember(slot.index) { mutableStateOf(false) }
    val swapLabel = when (pendingSwapSource) {
        null -> stringResource(R.string.quad_start_swap)
        slot.index -> stringResource(R.string.quad_cancel_swap)
        else -> stringResource(R.string.quad_complete_swap)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slot.video?.displayName ?: stringResource(R.string.quad_slot_actions)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(stringResource(R.string.quad_slot_label, slot.index + 1))
                }
                item { TextButton(onClick = onFocus) { Text(stringResource(R.string.quad_focus_layout)) } }
                item { TextButton(onClick = onEnlarge) { Text(stringResource(R.string.quad_enlarge)) } }
                item {
                    TextButton(onClick = onTogglePlayback) {
                        Text(stringResource(if (shouldPauseOnToggle(slot.playWhenReady, slot.playbackEnded)) R.string.common_pause else R.string.common_play))
                    }
                }
                item {
                    TextButton(onClick = onSwitchAudio) { Text(stringResource(R.string.quad_select_audio)) }
                }
                item {
                    DepthChoiceChip(selected = slot.repeatOne, onClick = onToggleRepeat, label = { Text(stringResource(R.string.quad_repeat_video)) })
                }
                item {
                    val start = slot.loopStartMs
                    val end = slot.loopEndMs
                    val positionText: (Long) -> String = { "${formatDuration(it)}.${(it % 1000) / 100}" }
                    Text(stringResource(R.string.quad_loop_range, start?.let(positionText) ?: "—", end?.let(positionText) ?: "—"))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { loopError = !onSetLoopStart(); if (!loopError) onDismiss() }) { Text(stringResource(R.string.quad_loop_set_a)) }
                        TextButton(enabled = slot.loopStartMs != null, onClick = { loopError = !onSetLoopEnd(); if (!loopError) onDismiss() }) { Text(stringResource(R.string.quad_loop_set_b)) }
                        TextButton(enabled = slot.loopStartMs != null, onClick = onClearLoop) { Text(stringResource(R.string.quad_loop_clear)) }
                    }
                }
                if (loopError) item { Text(stringResource(R.string.quad_loop_invalid), color = MaterialTheme.colorScheme.error) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlaybackSpeed.entries.forEach { speed ->
                            DepthChoiceChip(selected = speed == slot.playbackSpeed, onClick = { onSetSpeed(speed) }, label = { Text(speed.label) })
                        }
                    }
                }
                item {
                    TextButton(onClick = onReplace) {
                        Text(stringResource(R.string.quad_replace_video))
                    }
                }
                item {
                    TextButton(onClick = onSwap) {
                        Text(swapLabel)
                    }
                }
                item {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.quad_remove_video))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_done))
            }
        },
    )
}
