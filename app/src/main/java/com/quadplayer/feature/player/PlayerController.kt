package com.quadplayer.feature.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.quadplayer.R
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.AspectMode
import com.quadplayer.core.model.DecoderMode
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.PlaybackState
import com.quadplayer.core.model.VideoFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
)

data class PlayerUiState(
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Int = 0,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.X1_0,
    val aspectMode: AspectMode = AspectMode.FIT,
    val availableAudioTracks: List<TrackOption> = emptyList(),
    val availableSubtitleTracks: List<TrackOption> = emptyList(),
    val selectedExternalSubtitle: String? = null,
    val selectedExternalAudio: String? = null,
    val errorMessage: String? = null,
    val playWhenReady: Boolean = false,
)

@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val handleAudioFocus: Boolean = false,
    private val compactLocalBuffer: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dataSourceFactory = DefaultDataSource.Factory(context)
    private var trackSelector = DefaultTrackSelector(context)
    private var currentSettings = AppSettings()
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateStateFromPlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateStateFromPlayer()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateStateFromPlayer()
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateStateFromPlayer()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            updateStateFromPlayer(includeTracks = false)
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.value = _uiState.value.copy(
                playbackState = PlaybackState.ERROR,
                errorMessage = error.message ?: error.errorCodeName,
            )
        }
    }
    private var playerInternal: ExoPlayer = buildPlayer(currentSettings.decoderMode)
    val player: ExoPlayer
        get() = playerInternal
    var onPlayerRebuilt: ((ExoPlayer) -> Unit)? = null

    private var progressJob: Job? = null
    private val progressUpdatesEnabled = MutableStateFlow(true)
    private var currentVideo: VideoFile? = null
    private var externalSubtitleUri: Uri? = null
    private var externalSubtitleMime: String? = null
    private var subtitleSelectionExplicit = false
    private var externalAudioUri: Uri? = null

    init {
        playerInternal.addListener(playerListener)
        progressJob = scope.launch {
            combine(progressUpdatesEnabled, uiState.map { it.isPlaying }.distinctUntilChanged()) { enabled, playing -> enabled && playing }
                .distinctUntilChanged().collectLatest { shouldPoll ->
                    if (shouldPoll) while (true) {
                        updateStateFromPlayer(includeTracks = false)
                        delay(300)
                    }
            }
        }
    }

    suspend fun applySettings(settings: AppSettings) {
        val decoderChanged = settings.decoderMode != currentSettings.decoderMode
        currentSettings = settings
        if (decoderChanged) {
            rebuildPlayer(settings.decoderMode)
        }
        setPlaybackSpeed(settings.defaultPlaybackSpeed)
    }

    suspend fun load(video: VideoFile, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        val isNewVideo = currentVideo?.uriString != video.uriString
        if (isNewVideo) {
            externalSubtitleUri = null
            externalSubtitleMime = null
            subtitleSelectionExplicit = false
            externalAudioUri = null
        }
        _uiState.value = _uiState.value.copy(errorMessage = null)
        currentVideo = video
        if (isNewVideo) {
            val matchingFile = withContext(Dispatchers.IO) { findLocalSubtitle(video.localPath) }
            if (currentVideo?.uriString != video.uriString) return
            if (!subtitleSelectionExplicit && matchingFile != null) {
                externalSubtitleUri = Uri.fromFile(matchingFile)
                externalSubtitleMime = subtitleMimeType(matchingFile.name, null)
            }
        }
        withContext(Dispatchers.Main.immediate) {
            val source = buildMediaSource(video.uri, externalSubtitleUri, externalAudioUri)
            player.setMediaSource(source)
            player.prepare()
            player.seekTo(startPositionMs)
            player.playWhenReady = playWhenReady
        }
    }

    suspend fun setExternalSubtitle(uri: Uri?) {
        val videoBeforeQuery = currentVideo?.uriString
        val mime = uri?.let { selected -> withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = runCatching {
                resolver.query(selected, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull()
            subtitleMimeType(name ?: selected.lastPathSegment, runCatching { resolver.getType(selected) }.getOrNull())
        } }
        if (currentVideo?.uriString != videoBeforeQuery) return
        subtitleSelectionExplicit = true
        externalSubtitleUri = uri
        externalSubtitleMime = mime
        reloadCurrent()
    }

    suspend fun setExternalAudio(uri: Uri?) {
        externalAudioUri = uri
        reloadCurrent()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun clear() {
        currentVideo = null
        externalSubtitleUri = null
        externalSubtitleMime = null
        subtitleSelectionExplicit = false
        externalAudioUri = null
        player.pause()
        player.stop()
        player.clearMediaItems()
        _uiState.value = _uiState.value.copy(errorMessage = null)
        updateStateFromPlayer()
    }

    fun retry() {
        if (currentVideo == null) return
        _uiState.value = _uiState.value.copy(errorMessage = null)
        player.prepare()
        player.play()
    }

    fun play() = player.play()

    fun pause() = player.pause()

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        seekTo(player.currentPosition + deltaMs)
    }

    fun setProgressUpdatesEnabled(enabled: Boolean) {
        progressUpdatesEnabled.value = enabled
        if (enabled) updateStateFromPlayer(includeTracks = false)
    }

    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        player.setPlaybackSpeed(speed.value)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun cycleAspectMode() {
        val next = when (_uiState.value.aspectMode) {
            AspectMode.FIT -> AspectMode.FILL
            AspectMode.FILL -> AspectMode.ZOOM
            AspectMode.ZOOM -> AspectMode.SIXTEEN_NINE
            AspectMode.SIXTEEN_NINE -> AspectMode.FOUR_THREE
            AspectMode.FOUR_THREE -> AspectMode.ORIGINAL
            AspectMode.ORIGINAL -> AspectMode.FIT
        }
        _uiState.value = _uiState.value.copy(aspectMode = next)
    }

    fun selectAudioTrack(option: TrackOption?) {
        val builder = player.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, option == null)
        if (option != null) {
            val group = player.currentTracks.groups[option.groupIndex]
            builder.addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex)))
        }
        player.trackSelectionParameters = builder.build()
        updateStateFromPlayer()
    }

    fun selectSubtitleTrack(option: TrackOption?) {
        val builder = player.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, option == null && externalSubtitleUri == null)
        if (option != null) {
            val group = player.currentTracks.groups[option.groupIndex]
            builder.addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex)))
        }
        player.trackSelectionParameters = builder.build()
        updateStateFromPlayer()
    }

    fun release() {
        progressJob?.cancel()
        scope.cancel()
        player.removeListener(playerListener)
        player.release()
    }

    private suspend fun reloadCurrent() {
        val video = currentVideo ?: return
        val position = player.currentPosition
        val wasPlaying = player.playWhenReady
        load(video, position, playWhenReady = wasPlaying)
    }

    private fun buildPlayer(decoderMode: DecoderMode): ExoPlayer {
        trackSelector = DefaultTrackSelector(context)
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(decoderMode == DecoderMode.AUTO)
            .setMediaCodecSelector(buildMediaCodecSelector(decoderMode))

        val builder = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(AudioAttributes.DEFAULT, handleAudioFocus)
            .setHandleAudioBecomingNoisy(handleAudioFocus)
        if (compactLocalBuffer) {
            builder.setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(5_000, 15_000, 1_000, 2_000)
                .build())
        }
        return builder.build()
    }

    private suspend fun rebuildPlayer(decoderMode: DecoderMode) {
        withContext(Dispatchers.Main.immediate) {
            val oldPlayer = playerInternal
            val previousPosition = oldPlayer.currentPosition
            val previousVolume = oldPlayer.volume
            val previousRepeatMode = oldPlayer.repeatMode
            val previousPlayWhenReady = oldPlayer.playWhenReady
            val previousTrackParameters = oldPlayer.trackSelectionParameters

            val newPlayer = buildPlayer(decoderMode)
            playerInternal = newPlayer
            newPlayer.addListener(playerListener)
            newPlayer.volume = previousVolume
            newPlayer.repeatMode = previousRepeatMode
            newPlayer.trackSelectionParameters = previousTrackParameters

            val video = currentVideo
            if (video != null) {
                val source = buildMediaSource(video.uri, externalSubtitleUri, externalAudioUri)
                newPlayer.setMediaSource(source)
                newPlayer.prepare()
                newPlayer.seekTo(previousPosition)
                newPlayer.playWhenReady = previousPlayWhenReady
            }

            onPlayerRebuilt?.invoke(newPlayer)
            oldPlayer.removeListener(playerListener)
            oldPlayer.release()
            updateStateFromPlayer()
        }
    }

    private fun buildMediaCodecSelector(decoderMode: DecoderMode): MediaCodecSelector {
        if (decoderMode == DecoderMode.AUTO) {
            return MediaCodecSelector.DEFAULT
        }
        return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            val filtered = when (decoderMode) {
                DecoderMode.HARDWARE -> decoders.filter { it.hardwareAccelerated && !it.softwareOnly }
                DecoderMode.SOFTWARE -> decoders.filter { it.softwareOnly }
                DecoderMode.AUTO -> decoders
            }
            if (filtered.isNotEmpty()) filtered else decoders
        }
    }

    private fun buildMediaSource(
        videoUri: Uri,
        subtitleUri: Uri?,
        audioUri: Uri?,
    ) = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(
            MediaItem.Builder()
                .setUri(videoUri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(currentVideo?.displayName).build())
                .setSubtitleConfigurations(subtitleUri?.let { listOf(buildSubtitleConfiguration(it)) } ?: emptyList())
                .build(),
        ).let { videoSource ->
            if (audioUri == null) {
                videoSource
            } else {
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUri))
                MergingMediaSource(videoSource, audioSource)
            }
        }

    private fun buildSubtitleConfiguration(uri: Uri): MediaItem.SubtitleConfiguration {
        return MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(externalSubtitleMime ?: MimeTypes.TEXT_VTT)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    private fun updateStateFromPlayer(includeTracks: Boolean = true) {
        val player = playerInternal
        val tracks = player.currentTracks.groups
        _uiState.value = _uiState.value.copy(
            playbackState = if (_uiState.value.errorMessage != null) PlaybackState.ERROR else when (player.playbackState) {
                Player.STATE_IDLE -> PlaybackState.IDLE
                Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                Player.STATE_READY -> if (player.isPlaying) PlaybackState.PLAYING else PlaybackState.READY
                Player.STATE_ENDED -> PlaybackState.ENDED
                else -> PlaybackState.IDLE
            },
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            currentPositionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0L),
            bufferedPercent = player.bufferedPercentage,
            playbackSpeed = PlaybackSpeed.fromValue(player.playbackParameters.speed),
            availableAudioTracks = if (includeTracks) buildTrackOptions(tracks, C.TRACK_TYPE_AUDIO) else _uiState.value.availableAudioTracks,
            availableSubtitleTracks = if (includeTracks) buildTrackOptions(tracks, C.TRACK_TYPE_TEXT) else _uiState.value.availableSubtitleTracks,
            selectedExternalSubtitle = externalSubtitleUri?.lastPathSegment,
            selectedExternalAudio = externalAudioUri?.lastPathSegment,
        )
    }

    private fun buildTrackOptions(groups: List<Tracks.Group>, trackType: Int): List<TrackOption> {
        val options = mutableListOf<TrackOption>()
        groups.forEachIndexed { groupIndex, group ->
            if (group.type != trackType) return@forEachIndexed
            repeat(group.length) { trackIndex ->
                if (!group.isTrackSupported(trackIndex)) return@repeat
                val format = group.mediaTrackGroup.getFormat(trackIndex)
                val fallbackLabel = context.getString(R.string.player_track_fallback, trackIndex + 1)
                val label = buildString {
                    append(format.label ?: format.language ?: fallbackLabel)
                    if (format.bitrate > 0) append(" · ${format.bitrate / 1000} kbps")
                }
                options += TrackOption(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    label = label,
                    selected = group.isTrackSelected(trackIndex),
                )
            }
        }
        return options
    }

}
