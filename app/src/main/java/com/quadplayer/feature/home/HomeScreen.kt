package com.quadplayer.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import com.quadplayer.ui.theme.DepthChip
import androidx.compose.material3.Button
import com.quadplayer.ui.theme.DepthCard
import com.quadplayer.ui.theme.TrackDivider
import androidx.compose.material3.Icon
import com.quadplayer.ui.theme.RoundIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.quadplayer.ui.theme.QuietTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quadplayer.core.ui.rememberMediaAccess
import com.quadplayer.core.ui.VideoThumbnail
import com.quadplayer.R
import com.quadplayer.core.model.PlaylistSummary
import com.quadplayer.core.model.RecentPlayback
import com.quadplayer.core.model.VideoFile
import com.quadplayer.core.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenVideo: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyError by viewModel.historyError.collectAsStateWithLifecycle()
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val permission = rememberMediaAccess(viewModel::refresh)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var playlistTarget by remember { mutableStateOf<VideoFile?>(null) }



    if (!permission.available) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.home_permission_required))
                Button(onClick = { permission.request() }) {
                    Text(stringResource(R.string.home_grant_permission))
                }
            }
        }
        return
    }

    PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(if (compactHeight) 40.dp else 56.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            RoundIconButton(onClick = viewModel::refresh, enabled = !uiState.isRefreshing) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.home_refresh))
            }
        }
        if (permission.partial) {
            TextButton(onClick = permission.request) { Text(stringResource(R.string.media_partial_access)) }
        }
        if (!compactHeight) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_tab_library),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(if (compactHeight) 4.dp else 12.dp))
        QuietTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.home_search_label)) },
        )
        Spacer(Modifier.height(if (compactHeight) 4.dp else 12.dp))

        val tabs = listOf(
            stringResource(R.string.home_tab_library),
            stringResource(R.string.home_tab_recent),
            stringResource(R.string.home_tab_favorites),
            stringResource(R.string.home_tab_playlists),
        )
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { TrackDivider() },
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) },
                )
            }
        }
        Spacer(Modifier.height(if (compactHeight) 4.dp else 12.dp))

        when (selectedTab) {
            0 -> VideoList(
                videos = uiState.videos,
                emptyText = stringResource(R.string.home_empty_videos),
                onOpenVideo = onOpenVideo,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddToPlaylist = { playlistTarget = it },
            )

            1 -> RecentList(
                items = uiState.recent,
                emptyText = stringResource(R.string.home_empty_recent),
                onOpenVideo = onOpenVideo,
                onDelete = viewModel::deleteHistory,
                onClear = viewModel::clearHistory,
            )

            2 -> VideoList(
                videos = uiState.favorites,
                emptyText = stringResource(R.string.home_empty_favorites),
                onOpenVideo = onOpenVideo,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddToPlaylist = { playlistTarget = it },
            )

            else -> PlaylistList(
                playlists = uiState.playlists,
                emptyText = stringResource(R.string.home_empty_playlists),
                onOpenPlaylist = onOpenPlaylist,
                onCreatePlaylist = viewModel::createPlaylist,
            )
        }
    }
    }

    playlistTarget?.let { target ->
        PlaylistPickerDialog(
            playlists = uiState.playlists,
            onDismiss = { playlistTarget = null },
            onSelect = { playlistId ->
                viewModel.addVideoToPlaylist(playlistId, target.id)
                playlistTarget = null
            },
            onCreate = viewModel::createPlaylist,
        )
    }
    if (historyError) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHistoryError,
            text = { Text(stringResource(R.string.history_change_failed)) },
            confirmButton = { TextButton(onClick = viewModel::dismissHistoryError) { Text(stringResource(R.string.common_close)) } },
        )
    }
}

@Composable
private fun VideoList(
    videos: List<VideoFile>,
    emptyText: String,
    onOpenVideo: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddToPlaylist: (VideoFile) -> Unit,
) {
    if (videos.isEmpty()) {
        EmptyStateText(emptyText)
        return
    }

    LazyVerticalGrid(columns = GridCells.Adaptive(320.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        gridItems(videos, key = { it.id }) { video ->
            DepthCard(onClick = { onOpenVideo(video.id) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        VideoThumbnail(video)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(video.displayName, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.common_folder_duration, if (video.folderPath == "imported") stringResource(R.string.media_imported_folder) else video.folderName, formatDuration(video.durationMs)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton(onClick = { onOpenVideo(video.id) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.common_play))
                        }
                        RoundIconButton(onClick = { onToggleFavorite(video.id) }) {
                            Icon(
                                imageVector = if (video.isFavorite) {
                                    Icons.Rounded.Favorite
                                } else {
                                    Icons.Rounded.FavoriteBorder
                                },
                                contentDescription = stringResource(if (video.isFavorite) R.string.favorite_remove else R.string.favorite_add),
                            )
                        }
                        TextButton(onClick = { onAddToPlaylist(video) }) {
                            Text(stringResource(R.string.home_add_to_playlist))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentList(
    items: List<RecentPlayback>,
    emptyText: String,
    onOpenVideo: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
) {
    var confirmingClear by remember { mutableStateOf(false) }
    if (items.isEmpty()) {
        EmptyStateText(emptyText)
        return
    }

    LazyVerticalGrid(columns = GridCells.Adaptive(320.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmingClear = true }) { Text(stringResource(R.string.history_clear)) }
            }
        }
        gridItems(items, key = { it.video.id }) { item ->
            DepthCard(onClick = { onOpenVideo(item.video.id) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoThumbnail(item.video)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.video.displayName)
                    Text(
                        text = stringResource(R.string.home_resume_from, formatDuration(item.lastPositionMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.video.durationMs > 0) {
                        LinearProgressIndicator(
                            progress = { (item.lastPositionMs.toFloat() / item.video.durationMs).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    }
                    RoundIconButton(onClick = { onDelete(item.video.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.history_remove))
                    }
                }
            }
        }
    }
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_confirmation)) },
            confirmButton = { TextButton(onClick = { onClear(); confirmingClear = false }) { Text(stringResource(R.string.history_clear)) } },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun PlaylistList(
    playlists: List<PlaylistSummary>,
    emptyText: String,
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_my_playlists), style = MaterialTheme.typography.titleMedium)
            DepthChip(onClick = { creating = true }, label = { Text(stringResource(R.string.home_new_playlist)) })
        }
        if (playlists.isEmpty()) {
            EmptyStateText(emptyText)
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(320.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                gridItems(playlists, key = { it.id }) { playlist ->
                    DepthCard(onClick = { onOpenPlaylist(playlist.id) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.common_video_count, playlist.videoCount),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.home_create_playlist)) },
            text = {
                QuietTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.home_playlist_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onCreatePlaylist(text)
                            creating = false
                        }
                    },
                ) { Text(stringResource(R.string.common_create)) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun PlaylistPickerDialog(
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_add_to_playlist_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playlists.isEmpty()) {
                    Text(stringResource(R.string.home_empty_playlists))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_existing_playlists),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        playlists.forEach { playlist ->
                            Text(
                                text = playlist.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(playlist.id) }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                QuietTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.home_create_playlist)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        onCreate(newPlaylistName)
                        newPlaylistName = ""
                    }
                },
            ) { Text(stringResource(R.string.common_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
private fun EmptyStateText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
