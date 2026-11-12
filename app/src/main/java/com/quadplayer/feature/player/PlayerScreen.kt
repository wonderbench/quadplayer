package com.quadplayer.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import android.content.res.Configuration
import android.content.Context
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.ScreenRotationAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.quadplayer.R
import com.quadplayer.core.model.AspectMode
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.util.formatDuration
import com.quadplayer.core.ui.ImmersiveFullscreen
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var orientationOverride by rememberSaveable {
        mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var showMore by remember { mutableStateOf(false) }
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val component = activity as? ComponentActivity
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        component?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { component?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    var floatingActionsOnLeft by rememberSaveable { mutableStateOf(false) }

    val subtitlePicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.setExternalSubtitle(it)
        }
    }
    val audioPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.setExternalAudio(it)
        }
    }

    DisposableEffect(lifecycleOwner, state.settings.backgroundPlayback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onBackgroundStateChanged(
                    allowBackgroundPlayback = state.settings.backgroundPlayback,
                    isInPictureInPicture = activity?.isInPictureInPictureMode == true,
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation
        onDispose {
            if (activity != null && originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    LaunchedEffect(activity, orientationOverride) {
        if (orientationOverride != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activity?.requestedOrientation = orientationOverride
        }
    }

    LaunchedEffect(state.controlsVisible, state.playerState.isPlaying, state.video?.id, isSeeking, showMore, showAudioDialog, showSubtitleDialog) {
        if (state.controlsVisible && state.playerState.isPlaying && !isSeeking && !showMore && !showAudioDialog && !showSubtitleDialog) {
            delay(3000)
            viewModel.hideControls()
        }
    }

    LaunchedEffect(state.controlsVisible) {
        if (!state.controlsVisible) showMore = false
    }

    val playerMessage = state.statusMessage ?: if (state.playerState.errorMessage != null) {
        stringResource(R.string.player_error_failed)
    } else {
        null
    }
    val isLandscapeMode = when (orientationOverride) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> true

        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> false

        else -> configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    if (isLandscapeMode && !inPip) ImmersiveFullscreen()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        PlayerSurface(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
        )

        if (!inPip) GestureOverlay(
            modifier = Modifier.fillMaxSize(),
            context = context,
            seekSeconds = state.settings.doubleTapSeekSeconds,
            reverseDoubleTapSeek = state.settings.reverseDoubleTapSeek,
            onToggleControls = viewModel::toggleControls,
            onSeekBy = { viewModel.seekBy(it) },
            onLongPress = { viewModel.setSpeed(PlaybackSpeed.X2_0) },
        )

        if (!inPip) playerMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(if (isLandscapeMode) Modifier else Modifier.statusBarsPadding())
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(16.dp),
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (state.playerState.errorMessage != null) {
                        TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.player_retry)) }
                    }
                    if (state.statusMessage != null) {
                        TextButton(onClick = viewModel::clearStatusMessage) {
                            Text(stringResource(R.string.common_close))
                        }
                    }
                }
            }
        }

        if (!inPip) PlayerFloatingActions(
            modifier = Modifier
                .align(if (floatingActionsOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = 16.dp),
            isLandscapeMode = isLandscapeMode,
            seekSeconds = state.settings.doubleTapSeekSeconds,
            dockOnLeft = floatingActionsOnLeft,
            onDockSideChanged = { floatingActionsOnLeft = it },
            onToggleOrientation = {
                orientationOverride = if (isLandscapeMode) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            },
            onSeekForward = {
                viewModel.seekBy(state.settings.doubleTapSeekSeconds * 1000L)
            },
        )

        if (state.controlsVisible && !inPip) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (isLandscapeMode) Modifier else Modifier.navigationBarsPadding())
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .fillMaxWidth(),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                if (isLandscapeMode) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = viewModel::togglePlayPause) {
                                Text(stringResource(if (state.playerState.isPlaying) R.string.common_pause else R.string.common_play))
                            }
                            Text(formatDuration(if (isSeeking) seekPosition.toLong() else state.playerState.currentPositionMs))
                            Slider(
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                value = if (isSeeking) seekPosition else state.playerState.currentPositionMs.toFloat().coerceIn(0f, state.playerState.durationMs.coerceAtLeast(1L).toFloat()),
                                onValueChange = { isSeeking = true; seekPosition = it },
                                onValueChangeFinished = { viewModel.seekTo(seekPosition.toLong()); isSeeking = false },
                                valueRange = 0f..state.playerState.durationMs.coerceAtLeast(1L).toFloat(),
                            )
                            Text(formatDuration(state.playerState.durationMs))
                            Box {
                                TextButton(onClick = { showMore = true }) { Text("⋮") }
                                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.player_audio)) }, onClick = { showMore = false; showAudioDialog = true })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.player_subtitle)) }, onClick = { showMore = false; showSubtitleDialog = true })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.player_aspect)) }, onClick = { showMore = false; viewModel.cycleAspectMode() })
                                    PlaybackSpeed.entries.forEach { speed ->
                                        DropdownMenuItem(text = { Text(speed.label) }, onClick = { showMore = false; viewModel.setSpeed(speed) })
                                    }
                                    if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.player_pip)) }, onClick = {
                                            showMore = false
                                            runCatching { activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
                                        })
                                    }
                                }
                            }
                            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                        }
                    }
                } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                        Text(
                            text = state.video?.displayName ?: stringResource(R.string.player_title),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        TextButton(onClick = { showAudioDialog = true }) {
                            Text(stringResource(R.string.player_audio))
                        }
                        TextButton(onClick = { showSubtitleDialog = true }) {
                            Text(stringResource(R.string.player_subtitle))
                        }
                        TextButton(onClick = viewModel::cycleAspectMode) {
                            Text(stringResource(R.string.player_aspect))
                        }
                    }

                    Slider(
                        value = if (isSeeking) seekPosition else state.playerState.currentPositionMs.toFloat().coerceIn(0f, state.playerState.durationMs.coerceAtLeast(1L).toFloat()),
                        onValueChange = { isSeeking = true; seekPosition = it },
                        onValueChangeFinished = { viewModel.seekTo(seekPosition.toLong()); isSeeking = false },
                        valueRange = 0f..state.playerState.durationMs.coerceAtLeast(1L).toFloat(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatDuration(if (isSeeking) seekPosition.toLong() else state.playerState.currentPositionMs))
                        Text(formatDuration(state.playerState.durationMs))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = viewModel::togglePlayPause) {
                            Text(
                                if (state.playerState.isPlaying) {
                                    stringResource(R.string.common_pause)
                                } else {
                                    stringResource(R.string.common_play)
                                },
                            )
                        }
                        PlaybackSpeed.entries.forEach { speed ->
                            AssistChip(
                                onClick = { viewModel.setSpeed(speed) },
                                label = { Text(speed.label) },
                            )
                        }
                        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) TextButton(
                            onClick = {
                                runCatching { (context as? Activity)?.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder().build(),
                                ) }
                            },
                        ) {
                            Text(stringResource(R.string.player_pip))
                        }
                    }
                }
                }
            }
        }
    }

    if (showAudioDialog) {
        TrackDialog(
            title = stringResource(R.string.player_audio_tracks),
            disableLabel = stringResource(R.string.player_disable),
            doneLabel = stringResource(R.string.common_done),
            options = state.playerState.availableAudioTracks,
            onDismiss = { showAudioDialog = false },
            onSelect = {
                viewModel.selectAudioTrack(it)
                showAudioDialog = false
            },
            extraAction = {
                TextButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                    Text(stringResource(R.string.player_choose_external_audio))
                }
            },
        )
    }

    if (showSubtitleDialog) {
        TrackDialog(
            title = stringResource(R.string.player_subtitle_tracks),
            disableLabel = stringResource(R.string.player_disable),
            doneLabel = stringResource(R.string.common_done),
            options = state.playerState.availableSubtitleTracks,
            onDismiss = { showSubtitleDialog = false },
            onSelect = {
                viewModel.selectSubtitleTrack(it)
                showSubtitleDialog = false
            },
            extraAction = {
                TextButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.player_choose_external_subtitle))
                }
            },
        )
    }
}

@Composable
private fun PlayerFloatingActions(
    modifier: Modifier,
    isLandscapeMode: Boolean,
    seekSeconds: Int,
    dockOnLeft: Boolean,
    onDockSideChanged: (Boolean) -> Unit,
    onToggleOrientation: () -> Unit,
    onSeekForward: () -> Unit,
) {
    val density = LocalDensity.current
    val dragThreshold = remember(density) { with(density) { 56.dp.toPx() } }
    var draggedDistance by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .padding(vertical = 24.dp)
            .pointerInput(dockOnLeft) {
                detectDragGestures(
                    onDragStart = { draggedDistance = 0f },
                    onDragCancel = { draggedDistance = 0f },
                    onDragEnd = {
                        when {
                            draggedDistance <= -dragThreshold -> onDockSideChanged(true)
                            draggedDistance >= dragThreshold -> onDockSideChanged(false)
                        }
                        draggedDistance = 0f
                    },
                    onDrag = { change, dragAmount ->
                        draggedDistance += dragAmount.x
                    },
                )
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (dockOnLeft) Alignment.Start else Alignment.End,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = if (dockOnLeft) Alignment.Start else Alignment.End,
        ) {
            FloatingPlayerActionButton(
                icon = Icons.Rounded.ScreenRotationAlt,
                contentDescription = if (isLandscapeMode) {
                    stringResource(R.string.player_switch_portrait)
                } else {
                    stringResource(R.string.player_switch_landscape)
                },
                onClick = onToggleOrientation,
            )
            FloatingPlayerActionButton(
                icon = Icons.Rounded.FastForward,
                contentDescription = stringResource(R.string.player_seek_forward, seekSeconds),
                onClick = onSeekForward,
            )
        }
    }
}

@Composable
private fun FloatingPlayerActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 52.dp,
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@UnstableApi
@Composable
private fun PlayerSurface(
    modifier: Modifier,
    viewModel: PlayerViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aspectRatio = when (state.playerState.aspectMode) {
        AspectMode.SIXTEEN_NINE -> 16f / 9f
        AspectMode.FOUR_THREE -> 4f / 3f
        AspectMode.ORIGINAL -> {
            val width = state.video?.width ?: 0
            val height = state.video?.height ?: 0
            if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null
        }
        else -> null
    }
    val resizeMode = when (state.playerState.aspectMode) {
        AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = modifier.background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val playerModifier = if (aspectRatio != null) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            modifier = playerModifier,
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    player = viewModel.controller.player
                    this.resizeMode = resizeMode
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { playerView ->
                playerView.player = viewModel.controller.player
                playerView.resizeMode = resizeMode
                playerView.setShutterBackgroundColor(android.graphics.Color.BLACK)
                playerView.setBackgroundColor(android.graphics.Color.BLACK)
            },
        )
    }
}

@Composable
private fun GestureOverlay(
    modifier: Modifier,
    context: Context,
    seekSeconds: Int,
    reverseDoubleTapSeek: Boolean,
    onToggleControls: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onLongPress: () -> Unit,
) {
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val activity = context as? Activity

    Box(
        modifier = modifier
            .pointerInput(seekSeconds, reverseDoubleTapSeek) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        val seekDeltaMs = seekSeconds * 1000L
                        if (offset.x < size.width / 2f) {
                            onSeekBy(if (reverseDoubleTapSeek) seekDeltaMs else -seekDeltaMs)
                        } else {
                            onSeekBy(if (reverseDoubleTapSeek) -seekDeltaMs else seekDeltaMs)
                        }
                    },
                    onLongPress = { onLongPress() },
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    val delta = -dragAmount / size.height
                    if (change.position.x < size.width / 2f) {
                        val window = activity?.window ?: return@detectVerticalDragGestures
                        val current = if (window.attributes.screenBrightness < 0f) {
                            0.5f
                        } else {
                            window.attributes.screenBrightness
                        }
                        window.attributes = window.attributes.apply {
                            screenBrightness = (current + delta).coerceIn(0.05f, 1f)
                        }
                    } else {
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val target = (current + delta * max).toInt().coerceIn(0, max)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                    }
                }
            },
    )
}

@Composable
private fun TrackDialog(
    title: String,
    disableLabel: String,
    doneLabel: String,
    options: List<TrackOption>,
    onDismiss: () -> Unit,
    onSelect: (TrackOption?) -> Unit,
    extraAction: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = disableLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 4.dp),
                )
                options.forEach { option ->
                    Text(
                        text = if (option.selected) "✓ ${option.label}" else option.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 4.dp),
                    )
                }
                extraAction()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(doneLabel) }
        },
    )
}
