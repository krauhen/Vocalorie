package com.example.vocalorie.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VocalorieLightColorScheme = lightColorScheme(
    primary = Color(0xFFF77605),
    onPrimary = Color(0xFF1F1300),
    primaryContainer = Color(0xFFFFB600),
    onPrimaryContainer = Color(0xFF1F1300),
    secondary = Color(0xFFE5E5E5),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFFF3F4F6),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = Color(0xFFFFA000),
    onTertiary = Color(0xFF1F1300),
    tertiaryContainer = Color(0xFFFB8C00),
    onTertiaryContainer = Color(0xFF1F1300),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF3F3F46),
    outline = Color(0xFF71717A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val VocalorieDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB600),
    onPrimary = Color(0xFF1F1300),
    primaryContainer = Color(0xFFF77605),
    onPrimaryContainer = Color(0xFF1F1300),
    secondary = Color(0xFFD4D4D8),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF3F3F46),
    onSecondaryContainer = Color(0xFFE4E4E7),
    tertiary = Color(0xFFFFA000),
    onTertiary = Color(0xFF1F1300),
    tertiaryContainer = Color(0xFFFB8C00),
    onTertiaryContainer = Color(0xFF1F1300),
    background = Color(0xFF111111),
    onBackground = Color(0xFFE4E4E7),
    surface = Color(0xFF18181B),
    onSurface = Color(0xFFE4E4E7),
    surfaceVariant = Color(0xFF27272A),
    onSurfaceVariant = Color(0xFFD4D4D8),
    outline = Color(0xFF8A8A93),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun VocalorieTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) VocalorieDarkColorScheme else VocalorieLightColorScheme
    MaterialTheme(colorScheme = colors, content = content)
}
