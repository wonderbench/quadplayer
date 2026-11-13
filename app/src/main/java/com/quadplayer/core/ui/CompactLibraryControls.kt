package com.quadplayer.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quadplayer.R
import com.quadplayer.core.model.VideoFile
import com.quadplayer.core.util.formatDuration
import com.quadplayer.core.util.formatFileSize
import com.quadplayer.ui.theme.DepthCard
import com.quadplayer.ui.theme.RoundIconButton

@Composable
internal fun SearchPanelButton(expanded: Boolean, filtered: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (filtered) R.string.search_filter_active else R.string.search_filter_inactive)
    RoundIconButton(onClick = onClick, modifier = Modifier.semantics { stateDescription = state }) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.Search,
            contentDescription = stringResource(if (expanded) R.string.search_panel_close else R.string.search_panel_open),
            tint = if (filtered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun CompactVideoCard(
    video: VideoFile,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var menuOpen by remember(video.id) { mutableStateOf(false) }
    DepthCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VideoThumbnail(video, width = 64.dp, height = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(video.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.common_folder_duration, if (video.folderPath == "imported") stringResource(R.string.media_imported_folder) else video.folderName,
                        stringResource(R.string.folders_video_meta, formatDuration(video.durationMs), formatFileSize(video.sizeBytes))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            RoundIconButton(onClick = onToggleFavorite) {
                Icon(if (video.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(if (video.isFavorite) R.string.favorite_remove else R.string.favorite_add))
            }
            Box {
                RoundIconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.video_more_actions, video.displayName))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.home_add_to_playlist)) }, onClick = { menuOpen = false; onAddToPlaylist() })
                    if (onRemove != null) DropdownMenuItem(text = { Text(stringResource(R.string.import_remove_action)) }, onClick = { menuOpen = false; onRemove() })
                }
            }
        }
    }
}
