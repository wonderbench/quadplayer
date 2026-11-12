package com.quadplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quadplayer.core.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = EmberOrangeSoft,
    onPrimary = Color(0xFF4D1C0D),
    primaryContainer = Color(0xFF71351F),
    onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = SteelBlueSoft,
    background = InkBlack,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFF2A373C),
    onSurfaceVariant = DarkMuted,
    outlineVariant = DarkTrack,
)

private val LightColorScheme = lightColorScheme(
    primary = EmberOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF3F170B),
    secondary = SteelBlue,
    background = MistWhite,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = Color(0xFFE7ECEA),
    onSurfaceVariant = LightMuted,
    outlineVariant = LightTrack,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

@Composable
fun QuadPlayerTheme(themeMode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
