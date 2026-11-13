package com.quadplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun DepthCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.3f
    Card(
        onClick = onClick,
        modifier = modifier
            .shadow(8.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (dark) 0.35f else 0.11f), spotColor = Color.Black.copy(alpha = if (dark) 0.4f else 0.12f))
            .shadow(2.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.08f)),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Composable
fun RoundIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme.surface
    val dark = base.luminance() < 0.3f
    val highlight = Color.White.copy(alpha = if (dark) 0.14f else 0.8f).compositeOver(base)
    val shade = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.55f else 0.65f).compositeOver(base)
    val foreground = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp)) {
        Box(
            modifier = Modifier.size(42.dp)
                .shadow(6.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = if (dark) 0.38f else 0.13f), spotColor = Color.Black.copy(alpha = if (dark) 0.45f else 0.15f))
                .background(Brush.verticalGradient(listOf(highlight, shade)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(6.dp)
                    .background(Brush.verticalGradient(listOf(base, highlight)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides foreground, content = content)
            }
        }
    }
}

@Composable
fun QuietTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
) {
    val shape = RoundedCornerShape(14.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.shadow(4.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.1f)),
        label = label,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun TrackDivider(modifier: Modifier = Modifier) {
    val onePixel = with(LocalDensity.current) { 1f.toDp() }
    HorizontalDivider(modifier = modifier, thickness = onePixel, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun DepthChip(onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier.shadow(2.dp, RoundedCornerShape(12.dp), clip = false,
            ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.1f)),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
fun DepthChoiceChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = Modifier.shadow(2.dp, RoundedCornerShape(12.dp), clip = false,
            ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
fun RoundNavigationIcon(selected: Boolean, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme.surface
    val dark = base.luminance() < 0.3f
    val light = Color.White.copy(alpha = if (dark) 0.16f else 0.85f).compositeOver(base)
    val shade = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f).compositeOver(base)
    Box(
        modifier = if (selected) Modifier.size(40.dp)
            .shadow(5.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.15f))
            .background(Brush.verticalGradient(listOf(light, shade)), CircleShape)
        else Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.fillMaxSize().padding(6.dp).background(Brush.verticalGradient(listOf(base, light)), CircleShape), contentAlignment = Alignment.Center) { content() }
        } else content()
    }
}
