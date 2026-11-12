package com.quadplayer.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import com.quadplayer.ui.theme.TrackDivider
import androidx.compose.material3.Icon
import com.quadplayer.ui.theme.DepthChoiceChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quadplayer.R
import com.quadplayer.core.model.AppThemeMode
import com.quadplayer.core.model.DecoderMode
import com.quadplayer.core.model.PlaybackSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showAboutSheet by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showGrants by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        SettingSection(
            title = stringResource(R.string.settings_theme),
            description = stringResource(R.string.settings_theme_desc),
        ) {
            ChipRow(
                values = AppThemeMode.entries,
                selected = settings.themeMode,
                label = { themeModeLabel(it) },
                onClick = viewModel::setThemeMode,
            )
        }
        SettingSection(
            title = stringResource(R.string.settings_decoder),
            description = stringResource(R.string.settings_decoder_desc),
        ) {
            ChipRow(
                values = DecoderMode.entries,
                selected = settings.decoderMode,
                label = { decoderModeLabel(it) },
                onClick = viewModel::setDecoderMode,
            )
        }
        SettingSection(
            title = stringResource(R.string.settings_default_speed),
            description = stringResource(R.string.settings_default_speed_desc),
        ) {
            ChipRow(
                values = PlaybackSpeed.entries,
                selected = settings.defaultPlaybackSpeed,
                label = { it.label },
                onClick = viewModel::setSpeed,
            )
        }
        SettingSection(
            title = stringResource(R.string.settings_double_tap_seek),
            description = stringResource(R.string.settings_double_tap_seek_desc),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5, 10, 15, 30).forEach { seconds ->
                    DepthChoiceChip(
                        selected = seconds == settings.doubleTapSeekSeconds,
                        onClick = { viewModel.setDoubleTapSeconds(seconds) },
                        label = { Text(stringResource(R.string.settings_seek_seconds, seconds)) },
                    )
                }
            }
        }
        SettingSection(
            title = stringResource(R.string.settings_subtitles),
            description = stringResource(R.string.settings_subtitles_desc),
        ) {
            Text(stringResource(R.string.settings_subtitle_size), style = MaterialTheme.typography.labelLarge)
            ChipRow(values = listOf(0.8f, 1f, 1.25f, 1.5f), selected = settings.subtitleStyle.fontScale,
                label = { "${(it * 100).toInt()}%" }, onClick = viewModel::setSubtitleScale)
            Text(stringResource(R.string.settings_subtitle_color), style = MaterialTheme.typography.labelLarge)
            ChipRow(values = listOf(-1L, 0xFFFFFF00.toInt().toLong()), selected = settings.subtitleStyle.foregroundColor,
                label = { stringResource(if (it == -1L) R.string.settings_subtitle_white else R.string.settings_subtitle_yellow) }, onClick = viewModel::setSubtitleForeground)
            Text(stringResource(R.string.settings_subtitle_background), style = MaterialTheme.typography.labelLarge)
            ChipRow(values = listOf(0L, 0x73000000L, 0xFF000000.toInt().toLong()), selected = settings.subtitleStyle.backgroundColor,
                label = { stringResource(when (it) { 0L -> R.string.settings_subtitle_transparent; 0x73000000L -> R.string.settings_subtitle_translucent; else -> R.string.settings_subtitle_black }) }, onClick = viewModel::setSubtitleBackground)
        }
        SwitchRow(
            label = stringResource(R.string.settings_reverse_double_tap_seek),
            description = stringResource(R.string.settings_reverse_double_tap_seek_desc),
            checked = settings.reverseDoubleTapSeek,
            onCheckedChange = viewModel::setReverseDoubleTap,
        )
        SwitchRow(
            label = stringResource(R.string.settings_background_playback),
            description = stringResource(R.string.settings_background_playback_desc),
            checked = settings.backgroundPlayback,
            onCheckedChange = viewModel::setBackgroundPlayback,
        )
        if (Build.VERSION.SDK_INT >= 31 && LocalContext.current.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            SwitchRow(
                label = stringResource(R.string.settings_auto_pip),
                description = stringResource(R.string.settings_auto_pip_desc),
                checked = settings.autoPictureInPicture,
                onCheckedChange = viewModel::setAutoPictureInPicture,
            )
        }
        SwitchRow(
            label = stringResource(R.string.settings_auto_resume),
            description = stringResource(R.string.settings_auto_resume_desc),
            checked = settings.autoResume,
            onCheckedChange = viewModel::setAutoResume,
        )
        SwitchRow(
            label = stringResource(R.string.settings_crossfade_audio),
            description = stringResource(R.string.settings_crossfade_audio_desc),
            checked = settings.crossfadeAudioSwitching,
            onCheckedChange = viewModel::setCrossfade,
        )
        AboutSoftwareEntry(
            title = stringResource(R.string.settings_about_software),
            description = stringResource(R.string.settings_about_software_desc),
            onClick = { showAboutSheet = true },
        )
        AboutSoftwareEntry(
            title = stringResource(R.string.settings_open_source_licenses),
            description = stringResource(R.string.settings_open_source_licenses_desc),
            onClick = { showLicenses = true },
        )
        AboutSoftwareEntry(
            title = stringResource(R.string.settings_grants_title),
            description = stringResource(R.string.settings_grants_entry_desc),
            onClick = { showGrants = true },
        )
    }

    if (showAboutSheet) {
        AboutSoftwareSheet(onDismiss = { showAboutSheet = false })
    }
    if (showLicenses) {
        ThirdPartyNoticesDialog(onDismiss = { showLicenses = false })
    }
    if (showGrants) {
        PersistedGrantManager(onDismiss = { showGrants = false })
    }
}

@Composable
internal fun ThirdPartyNoticesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val loading = stringResource(R.string.settings_licenses_loading)
    val failed = stringResource(R.string.settings_licenses_failed)
    val notices by produceState(loading, context, loading, failed) {
        value = withContext(Dispatchers.IO) {
            try {
                listOf("third_party_summary.txt", "release_components.txt", "inter_tight_source.txt",
                    "inter_tight_ofl.txt", "jakarta_inject_notice.txt", "apache_2_0.txt")
                    .joinToString("\n\n") { file -> context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }
            } catch (_: IOException) { failed }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_open_source_licenses)) },
        text = { Text(notices, modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun SettingSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutSoftwareEntry(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSoftwareSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val content = aboutSoftwareContent(context)
    val sections = aboutVisualSections(context)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_about_software),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_software_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AboutSectionCard(section = sections[0]) {
                Text(
                    text = content.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AboutSectionCard(section = sections[1]) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    content.featureGroups.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            group.items.forEach { item ->
                                AboutBulletItem(text = item)
                            }
                        }
                    }
                }
            }

            AboutSectionCard(section = sections[2]) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content.requirements.forEachIndexed { index, item ->
                        if (index > 0) {
                            TrackDivider()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = item.title,
                                modifier = Modifier.weight(0.34f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = item.value,
                                modifier = Modifier.weight(0.66f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    ) {
                        Text(
                            text = content.requirementHint,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AboutSectionCard(section = sections[3]) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    content.guideGroups.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            group.steps.forEachIndexed { index, step ->
                                AboutStepItem(index = index + 1, text = step)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSectionCard(
    section: AboutVisualSection,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.medium,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            content()
        }
    }
}

@Composable
private fun AboutBulletItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small,
                ),
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutStepItem(
    index: Int,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        ) {
            Text(
                text = index.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> ChipRow(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onClick: (T) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            DepthChoiceChip(
                selected = value == selected,
                onClick = { onClick(value) },
                label = {
                    Text(label(value))
                },
            )
        }
    }
}

@Composable
private fun themeModeLabel(mode: AppThemeMode): String {
    return when (mode) {
        AppThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
        AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }
}

@Composable
private fun decoderModeLabel(mode: DecoderMode): String {
    return when (mode) {
        DecoderMode.AUTO -> stringResource(R.string.settings_decoder_auto)
        DecoderMode.HARDWARE -> stringResource(R.string.settings_decoder_hardware)
        DecoderMode.SOFTWARE -> stringResource(R.string.settings_decoder_software)
    }
}
