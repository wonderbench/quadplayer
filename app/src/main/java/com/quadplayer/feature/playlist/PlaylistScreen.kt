package com.quadplayer.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import com.quadplayer.ui.theme.DepthCard
import com.quadplayer.ui.theme.RoundIconButton
import com.quadplayer.ui.theme.QuietTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quadplayer.R
import com.quadplayer.core.util.formatDuration

@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onOpenVideo: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        Text(uiState.name ?: stringResource(R.string.playlist_title), style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { uiState.videos.firstOrNull()?.let { onOpenVideo(it.id) } }, enabled = uiState.videos.isNotEmpty()) {
                Text(stringResource(R.string.playlist_play_all))
            }
            TextButton(onClick = { renaming = true }, enabled = uiState.name != null) { Text(stringResource(R.string.playlist_rename)) }
            TextButton(onClick = { deleting = true }, enabled = uiState.name != null) { Text(stringResource(R.string.playlist_delete)) }
        }
        if (uiState.videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.playlist_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(uiState.videos, key = { _, item -> item.id }) { index, video ->
                    DepthCard(onClick = { onOpenVideo(video.id) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(video.displayName)
                                Text(
                                    text = formatDuration(video.durationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row {
                                RoundIconButton(onClick = { viewModel.moveUp(video.id) }, enabled = index > 0) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.playlist_move_up))
                                }
                                RoundIconButton(onClick = { viewModel.moveDown(video.id) }, enabled = index < uiState.videos.lastIndex) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.playlist_move_down))
                                }
                                RoundIconButton(onClick = { viewModel.remove(video.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.playlist_remove))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (renaming) {
        var name by remember(uiState.playlistId) { mutableStateOf(uiState.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.playlist_rename)) },
            text = { QuietTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.rename(name); renaming = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.playlist_save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.playlist_delete)) },
            text = { Text(stringResource(R.string.playlist_delete_confirm, uiState.name.orEmpty())) },
            confirmButton = { TextButton(onClick = { deleting = false; viewModel.delete(onBack) }) { Text(stringResource(R.string.playlist_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
