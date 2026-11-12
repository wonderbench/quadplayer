package com.quadplayer.feature.filebrowser

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.quadplayer.R
import com.quadplayer.core.model.PlaylistSummary
import com.quadplayer.core.model.VideoFile
import com.quadplayer.core.util.formatDuration
import com.quadplayer.core.util.formatFileSize
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalPermissionsApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel,
    onOpenVideo: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberPermissionState(
        permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        },
    )
    var playlistTarget by remember { mutableStateOf<VideoFile?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(permission.status.isGranted) {
        if (permission.status.isGranted) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.openPlaylistEvents.collectLatest { playlistId ->
            onOpenPlaylist(playlistId)
        }
    }

    if (!permission.status.isGranted) {
        PermissionRequired(onGrant = { permission.launchPermissionRequest() })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.folders_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_refresh))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScopeChip(
                label = uiState.selectedFolder?.name ?: stringResource(R.string.folders_all_videos),
                onClick = { showFolderPicker = true },
                modifier = Modifier.weight(1f),
            )
            AssistChip(
                onClick = viewModel::createPlaylistFromCurrentView,
                enabled = uiState.videos.isNotEmpty(),
                label = { Text(stringResource(R.string.folders_generate_playlist)) },
            )
        }

        OutlinedTextField(
            value = uiState.videoQuery,
            onValueChange = viewModel::updateVideoQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.folders_search_videos)) },
            singleLine = true,
        )

        uiState.selectedFolder?.let { folder ->
            Text(
                text = stringResource(R.string.folders_current_path, folder.path),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = stringResource(
                R.string.folders_current_view_summary,
                uiState.videos.size,
                formatDuration(uiState.totalDurationMs),
                formatFileSize(uiState.totalSizeBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FileBrowserSortMode.entries.forEach { mode ->
                AssistChip(
                    onClick = { viewModel.setSortMode(mode) },
                    label = {
                        Text(
                            text = when (mode) {
                                FileBrowserSortMode.NAME -> stringResource(R.string.folders_sort_name)
                                FileBrowserSortMode.DATE -> stringResource(R.string.folders_sort_date)
                                FileBrowserSortMode.DURATION -> stringResource(R.string.folders_sort_duration)
                                FileBrowserSortMode.SIZE -> stringResource(R.string.folders_sort_size)
                            },
                        )
                    },
                    enabled = uiState.sortMode != mode,
                )
            }
        }

        HorizontalDivider()

        if (uiState.videos.isEmpty()) {
            EmptyBrowserText(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.folders_empty_videos),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(uiState.videos, key = { it.id }) { video ->
                    VideoRow(
                        video = video,
                        onOpenVideo = onOpenVideo,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlaylist = { playlistTarget = it },
                    )
                }
            }
        }
    }

    if (showFolderPicker) {
        ModalBottomSheet(onDismissRequest = { showFolderPicker = false }) {
            FolderPickerSheet(
                uiState = uiState,
                onSelectFolder = { path ->
                    viewModel.selectFolder(path)
                    showFolderPicker = false
                },
                onUpdateQuery = viewModel::updateFolderQuery,
            )
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
            onCreate = { name ->
                viewModel.createPlaylistAndAdd(name, target.id)
                playlistTarget = null
            },
        )
    }
}

@Composable
private fun PermissionRequired(onGrant: () -> Unit) {
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
            Button(onClick = onGrant) {
                Text(stringResource(R.string.home_grant_permission))
            }
        }
    }
}

@Composable
private fun ScopeChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderPickerSheet(
    uiState: FileBrowserUiState,
    onSelectFolder: (String?) -> Unit,
    onUpdateQuery: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.folders_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = uiState.folderQuery,
            onValueChange = onUpdateQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.folders_search_folders)) },
            singleLine = true,
        )
        FolderChoiceRow(
            title = stringResource(R.string.folders_all_videos),
            countLabel = stringResource(R.string.common_video_count, uiState.totalVideoCount),
            selected = uiState.selectedFolder == null,
            onClick = { onSelectFolder(null) },
        )
        if (uiState.folders.isEmpty()) {
            EmptyBrowserText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                text = stringResource(R.string.folders_empty_folders),
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(uiState.folders, key = { it.path }) { folder ->
                    FolderChoiceRow(
                        title = folder.name,
                        countLabel = stringResource(R.string.common_video_count, folder.videoCount),
                        selected = uiState.selectedFolder?.path == folder.path,
                        onClick = { onSelectFolder(folder.path) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderChoiceRow(
    title: String,
    countLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VideoRow(
    video: VideoFile,
    onOpenVideo: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddToPlaylist: (VideoFile) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenVideo(video.id) }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.common_folder_duration,
                        video.folderName,
                        formatDuration(video.durationMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.folders_video_meta,
                        formatDuration(video.durationMs),
                        formatFileSize(video.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onToggleFavorite(video.id) }) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(R.string.home_tab_favorites),
                )
            }
        }
        Text(
            text = stringResource(R.string.home_add_to_playlist),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onAddToPlaylist(video) },
        )
        HorizontalDivider()
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
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.home_create_playlist)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        onCreate(newPlaylistName)
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
private fun EmptyBrowserText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
