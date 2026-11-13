package com.quadplayer.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.quadplayer.R
import com.quadplayer.core.data.DocumentAccessGrants
import com.quadplayer.core.data.DocumentGrantItem
import com.quadplayer.core.data.DocumentGrantToken
import com.quadplayer.core.data.GrantReleaseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PersistedGrantManager(onDismiss: () -> Unit) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val windowHeightDp = with(LocalDensity.current) { windowHeight.toDp() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var grants by remember { mutableStateOf<List<DocumentGrantItem>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<Pair<DocumentGrantItem, DocumentGrantToken>?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(revision) {
        grants = null
        loadFailed = false
        try {
            grants = withContext(Dispatchers.IO) { DocumentAccessGrants.listReadGrants(context.contentResolver) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailed = true }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = windowHeightDp * 0.82f).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.settings_grants_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.settings_grants_desc), style = MaterialTheme.typography.bodyMedium)
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                when {
                    loadFailed -> Text(stringResource(R.string.settings_grants_load_failed))
                    grants == null -> Text(stringResource(R.string.settings_grants_loading))
                    grants!!.isEmpty() -> Text(stringResource(R.string.settings_grants_empty))
                    else -> GrantList(
                        grants = grants!!,
                        enabled = !busy,
                        modifier = Modifier.weight(1f, fill = false),
                        onRelease = { item -> pending = item to DocumentAccessGrants.capture(item.uri) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { error = null; revision++ }, enabled = !busy) { Text(stringResource(R.string.settings_grants_refresh)) }
                    TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_close)) }
                }
            }
        }
    }
    pending?.let { (item, token) ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text(stringResource(R.string.settings_grant_revoke_title)) },
            text = {
                Column(Modifier.heightIn(max = windowHeightDp * 0.45f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_grant_revoke_confirmation, item.label))
                    Text(item.uri.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { DocumentAccessGrants.releaseRead(context.contentResolver, token) }
                        busy = false
                        pending = null
                        when (result) {
                            GrantReleaseResult.RELEASED -> revision++
                            GrantReleaseResult.CHANGED -> error = R.string.settings_grant_changed
                            GrantReleaseResult.FAILED -> error = R.string.settings_grant_release_failed
                        }
                    }
                }) { Text(stringResource(R.string.settings_grant_revoke_action)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
internal fun GrantList(grants: List<DocumentGrantItem>, enabled: Boolean, modifier: Modifier = Modifier, onRelease: (DocumentGrantItem) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(grants, key = { it.uri.toString() }) { item ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(item.label, style = MaterialTheme.typography.titleSmall)
                    Text(item.uri.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row { TextButton(enabled = enabled, onClick = { onRelease(item) }) { Text(stringResource(R.string.settings_grant_revoke_action)) } }
                }
            }
        }
    }
}
