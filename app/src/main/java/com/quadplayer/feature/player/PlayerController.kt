package com.quadplayer.feature.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.core.net.toUri
import org.json.JSONObject
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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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

private const val EXTERNAL_SUBTITLE_TRACK_ID = "quadplayer-external-subtitle"
private fun isExternalSubtitleTrack(id: String?): Boolean =
    id == EXTERNAL_SUBTITLE_TRACK_ID || id?.endsWith(":$EXTERNAL_SUBTITLE_TRACK_ID") == true

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
    val failure: PlaybackFailure? = null,
    val playWhenReady: Boolean = false,
    val videoSize: VideoSize = VideoSize.UNKNOWN,
    val repeatOne: Boolean = false,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
)

@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val handleAudioFocus: Boolean = false,
    private val compactLocalBuffer: Boolean = false,
    private val beforeSubtitleInspection: suspend (Uri) -> Unit = {},
    private val rememberTrackChoices: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dataSourceFactory = DefaultDataSource.Factory(context)
    private var trackSelector = DefaultTrackSelector(context)
    private var currentSettings = AppSettings()
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val start = _uiState.value.loopStartMs
            if (playbackState == Player.STATE_ENDED && playerInternal.playWhenReady && start != null && _uiState.value.loopEndMs != null) {
                playerInternal.seekTo(start)
                return
            }
            updateStateFromPlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateStateFromPlayer()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateStateFromPlayer()
        }

        override fun onTracksChanged(tracks: Tracks) {
            restoreTrackChoices(tracks)
            if (pendingExternalSubtitleSelection) {
                val group = tracks.groups.firstOrNull { candidate ->
                    candidate.type == C.TRACK_TYPE_TEXT && (0 until candidate.length).any { isExternalSubtitleTrack(candidate.getTrackFormat(it).id) }
                }
                if (group != null) {
                    val index = (0 until group.length).first { isExternalSubtitleTrack(group.getTrackFormat(it).id) }
                    pendingExternalSubtitleSelection = false
                    playerInternal.trackSelectionParameters = playerInternal.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                        .build()
                }
            }
            updateStateFromPlayer()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _uiState.value = _uiState.value.copy(videoSize = videoSize)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.value = _uiState.value.copy(repeatOne = repeatMode == Player.REPEAT_MODE_ONE)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            updateStateFromPlayer(includeTracks = false)
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.value = _uiState.value.copy(
                playbackState = PlaybackState.ERROR,
                errorMessage = error.message ?: error.errorCodeName,
                failure = classifyPlaybackFailure(error.errorCode, error.cause),
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
    private var subtitleSelectionGeneration = 0L
    private var selectExternalOnNextLoad = false
    private var pendingExternalSubtitleSelection = false
    private var externalAudioUri: Uri? = null
    private var released = false
    private var loadGeneration = 0L
    private val trackChoices by lazy { TrackChoices(context) }
    private var audioChoiceGeneration = 0L
    private var pendingAudioChoice: JSONObject? = null
    private var pendingTextChoice: JSONObject? = null

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
        scope.launch {
            uiState.map { Triple(it.loopStartMs, it.loopEndMs, it.isPlaying) }.distinctUntilChanged()
                .collectLatest { (start, end, playing) ->
                    if (start != null && end != null && playing) while (true) {
                        if (player.currentPosition >= end) player.seekTo(start)
                        delay(40)
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

    suspend fun load(video: VideoFile, startPositionMs: Long = 0L, playWhenReady: Boolean = true): Boolean {
        if (released) return false
        val request = ++loadGeneration
        val isNewVideo = currentVideo?.uriString != video.uriString
        if (isNewVideo) {
            subtitleSelectionGeneration++
            audioChoiceGeneration++
            pendingAudioChoice = null
            pendingTextChoice = null
            selectExternalOnNextLoad = false
            pendingExternalSubtitleSelection = false
            externalSubtitleUri = null
            externalSubtitleMime = null
            subtitleSelectionExplicit = false
            externalAudioUri = null
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
        }
        _uiState.value = _uiState.value.copy(errorMessage = null, failure = null, videoSize = if (isNewVideo) VideoSize.UNKNOWN else _uiState.value.videoSize)
        currentVideo = video
        if (isNewVideo) {
            val textGeneration = subtitleSelectionGeneration
            val audioGeneration = audioChoiceGeneration
            val (matchingFile, choices) = withContext(Dispatchers.IO) {
                findLocalSubtitle(video.localPath) to if (rememberTrackChoices) {
                    listOf(trackChoices.get(video.uriString, "audio"), trackChoices.get(video.uriString, "text"),
                        trackChoices.get(video.uriString, "audio_source"), trackChoices.get(video.uriString, "text_source"))
                } else null
            }
            if (released || request != loadGeneration) return false
            if (choices != null && audioGeneration == audioChoiceGeneration) {
                pendingAudioChoice = choices[0]
                val source = choices[2]
                if (source?.optString("mode") == "external") {
                    val uri = source.optString("uri").toUri()
                    val available = withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { } != null }.getOrDefault(false)
                    }
                    if (released || request != loadGeneration) return false
                    if (audioGeneration == audioChoiceGeneration && available) externalAudioUri = uri
                }
            }
            if (choices != null && textGeneration == subtitleSelectionGeneration) {
                val textChoice = choices[1]
                if (textChoice?.optString("mode") == "external") {
                    val uri = textChoice.optString("uri").toUri()
                    val info = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { }
                                ?: error("Subtitle is unavailable")
                            inspectSubtitleMime(uri)
                        }.getOrNull()
                    }
                    if (released || request != loadGeneration) return false
                    if (textGeneration == subtitleSelectionGeneration && info != null) {
                        externalSubtitleUri = uri
                        externalSubtitleMime = info
                        subtitleSelectionExplicit = true
                        selectExternalOnNextLoad = true
                    }
                } else if (textChoice?.optString("mode") == "track" && choices[3]?.optString("mode") == "external") {
                    val uri = choices[3]!!.optString("uri").toUri()
                    val info = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { }
                                ?: error("Subtitle is unavailable")
                            inspectSubtitleMime(uri)
                        }.getOrNull()
                    }
                    if (released || request != loadGeneration) return false
                    if (textGeneration == subtitleSelectionGeneration) {
                        if (info != null) {
                            externalSubtitleUri = uri
                            externalSubtitleMime = info
                        }
                        pendingTextChoice = textChoice
                    }
                } else {
                    pendingTextChoice = textChoice
                    if (textChoice?.optString("mode") == "none") subtitleSelectionExplicit = true
                }
            }
            if (!subtitleSelectionExplicit && matchingFile != null && pendingTextChoice == null) {
                externalSubtitleUri = Uri.fromFile(matchingFile)
                externalSubtitleMime = subtitleMimeType(matchingFile.name, null)
                selectExternalOnNextLoad = true
            }
            applyDisabledChoices()
        }
        return withContext(Dispatchers.Main.immediate) {
            if (released || request != loadGeneration) return@withContext false
            val source = buildMediaSource(video.uri, externalSubtitleUri, externalAudioUri)
            val selectExternal = selectExternalOnNextLoad || pendingExternalSubtitleSelection
            pendingExternalSubtitleSelection = false
            player.setMediaSource(source)
            pendingExternalSubtitleSelection = selectExternal
            selectExternalOnNextLoad = false
            player.prepare()
            player.seekTo(startPositionMs)
            player.playWhenReady = playWhenReady
            true
        }
    }

    suspend fun setExternalSubtitle(uri: Uri?) {
        val request = ++subtitleSelectionGeneration
        pendingTextChoice = null
        val videoBeforeQuery = currentVideo?.uriString
        val mime = uri?.let { selected -> withContext(Dispatchers.IO) {
            beforeSubtitleInspection(selected)
            inspectSubtitleMime(selected)
        } }
        if (released || currentVideo?.uriString != videoBeforeQuery || request != subtitleSelectionGeneration) return
        subtitleSelectionExplicit = true
        externalSubtitleUri = uri
        externalSubtitleMime = mime
        selectExternalOnNextLoad = uri != null
        pendingExternalSubtitleSelection = false
        if (uri != null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
        if (rememberTrackChoices && videoBeforeQuery != null) {
            val choice = JSONObject().put("mode", if (uri == null) "none" else "external")
            if (uri != null) choice.put("uri", uri.toString())
            trackChoices.put(videoBeforeQuery, "text", choice)
            trackChoices.put(videoBeforeQuery, "text_source", choice)
        }
        reloadCurrent()
    }

    suspend fun setExternalAudio(uri: Uri?) {
        audioChoiceGeneration++
        pendingAudioChoice = null
        externalAudioUri = uri
        if (uri != null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build()
        }
        currentVideo?.uriString?.takeIf { rememberTrackChoices }?.let { video ->
            val choice = JSONObject().put("mode", if (uri == null) "none" else "external")
            if (uri != null) choice.put("uri", uri.toString())
            trackChoices.put(video, "audio_source", choice)
            if (uri != null) trackChoices.put(video, "audio", JSONObject().put("mode", "auto"))
        }
        reloadCurrent()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun clear() {
        loadGeneration++
        subtitleSelectionGeneration++
        pendingAudioChoice = null
        pendingTextChoice = null
        selectExternalOnNextLoad = false
        pendingExternalSubtitleSelection = false
        currentVideo = null
        externalSubtitleUri = null
        externalSubtitleMime = null
        subtitleSelectionExplicit = false
        externalAudioUri = null
        player.pause()
        player.stop()
        player.clearMediaItems()
        _uiState.value = _uiState.value.copy(errorMessage = null, failure = null, videoSize = VideoSize.UNKNOWN)
        updateStateFromPlayer()
    }

    fun retry() {
        if (currentVideo == null) return
        _uiState.value = _uiState.value.copy(errorMessage = null, failure = null)
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

    fun setLoopStart(): Boolean {
        if (!player.isCurrentMediaItemSeekable) return false
        _uiState.value = _uiState.value.copy(loopStartMs = player.currentPosition.coerceAtLeast(0L), loopEndMs = null)
        return true
    }

    fun setLoopEnd(): Boolean {
        val start = _uiState.value.loopStartMs ?: return false
        val end = player.currentPosition
        if (!player.isCurrentMediaItemSeekable || end - start < 250L) return false
        player.repeatMode = Player.REPEAT_MODE_OFF
        _uiState.value = _uiState.value.copy(loopEndMs = end)
        player.seekTo(start)
        return true
    }

    fun clearLoop() {
        _uiState.value = _uiState.value.copy(loopStartMs = null, loopEndMs = null)
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
        audioChoiceGeneration++
        pendingAudioChoice = null
        saveTrackChoice("audio", option)
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
        subtitleSelectionGeneration++
        pendingTextChoice = null
        saveTrackChoice("text", option)
        subtitleSelectionExplicit = true
        selectExternalOnNextLoad = false
        pendingExternalSubtitleSelection = false
        val builder = player.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, option == null)
        if (option != null) {
            val group = player.currentTracks.groups[option.groupIndex]
            builder.addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex)))
        }
        player.trackSelectionParameters = builder.build()
        updateStateFromPlayer()
    }

    fun release() {
        if (released) return
        released = true
        loadGeneration++
        subtitleSelectionGeneration++
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
        // Media3 1.5's extraction-time subtitle parser can miss cues spanning a seek.
        // Legacy decoding loads the complete sidecar so choosing it mid-cue works.
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildTextRenderers(context: Context, output: TextOutput, outputLooper: Looper, extensionRendererMode: Int, out: ArrayList<Renderer>) {
                out.add(TextRenderer(output, outputLooper).apply { experimentalSetLegacyDecodingEnabled(true) })
            }
        }
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

    private fun inspectSubtitleMime(selected: Uri): String {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(selected, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return subtitleMimeType(name ?: selected.lastPathSegment, runCatching { resolver.getType(selected) }.getOrNull())
    }

    private fun saveTrackChoice(type: String, option: TrackOption?) {
        if (!rememberTrackChoices) return
        val video = currentVideo?.uriString ?: return
        val choice = if (option == null) JSONObject().put("mode", "disabled") else {
            val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            if (option.trackIndex !in 0 until group.length) return
            val format = group.getTrackFormat(option.trackIndex)
            if (type == "text" && isExternalSubtitleTrack(format.id) && externalSubtitleUri != null) {
                JSONObject().put("mode", "external").put("uri", externalSubtitleUri.toString())
            } else JSONObject().put("mode", "track").put("fingerprint", trackFingerprint(format))
        }
        if (type == "text" && externalSubtitleUri != null) {
            trackChoices.put(video, "text_source", JSONObject().put("mode", "external").put("uri", externalSubtitleUri.toString()))
        }
        trackChoices.put(video, type, choice)
    }

    private fun applyDisabledChoices() {
        val builder = player.trackSelectionParameters.buildUpon()
        if (pendingAudioChoice?.optString("mode") == "disabled") {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            pendingAudioChoice = null
        }
        if (pendingTextChoice?.optString("mode") == "disabled") {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            pendingTextChoice = null
        }
        if (pendingTextChoice?.optString("mode") == "none") pendingTextChoice = null
        player.trackSelectionParameters = builder.build()
    }

    private fun restoreTrackChoices(tracks: Tracks) {
        // Empty track notifications also occur when the previous source is cleared.
        if (tracks.groups.isEmpty() || player.currentMediaItem?.localConfiguration?.uri != currentVideo?.uri) return
        fun restore(choice: JSONObject?, type: Int) {
            if (choice?.optString("mode") != "track") return
            val fingerprint = choice.optString("fingerprint")
            val matches = tracks.groups.filter { it.type == type }.flatMap { group ->
                (0 until group.length).filter { group.isTrackSupported(it) && trackFingerprint(group.getTrackFormat(it)) == fingerprint }
                    .map { group to it }
            }
            // Never guess if the file changed or has indistinguishable tracks.
            if (matches.size == 1) {
                val (group, index) = matches.single()
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(type).setTrackTypeDisabled(type, false)
                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(index))).build()
            }
        }
        val audio = pendingAudioChoice
        val text = pendingTextChoice
        pendingAudioChoice = null
        pendingTextChoice = null
        restore(audio, C.TRACK_TYPE_AUDIO)
        restore(text, C.TRACK_TYPE_TEXT)
    }

    private fun buildMediaSource(
        videoUri: Uri,
        subtitleUri: Uri?,
        audioUri: Uri?,
    ) = DefaultMediaSourceFactory(dataSourceFactory)
        .experimentalParseSubtitlesDuringExtraction(false)
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
            .setId(EXTERNAL_SUBTITLE_TRACK_ID)
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
