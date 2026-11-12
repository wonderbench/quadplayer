package com.quadplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.quadplayer.core.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = EmberOrangeSoft,
    secondary = SteelBlueSoft,
    tertiary = EmberOrange,
    background = InkBlack,
    surface = InkBlack,
)

private val LightColorScheme = lightColorScheme(
    primary = EmberOrange,
    secondary = SteelBlue,
    tertiary = EmberOrangeSoft,
    background = MistWhite,
    surface = MistWhite,
)

@Composable
fun QuadPlayerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
