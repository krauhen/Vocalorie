package com.example.vocalorie.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.vocalorie.settings.ThemeColors

private fun onColorFor(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)

private fun containerFor(color: Color, isDark: Boolean): Color =
    if (isDark) lerp(color, Color.Black, 0.35f) else lerp(color, Color.White, 0.55f)

private fun surfaceVariantFor(background: Color, isDark: Boolean): Color =
    if (isDark) lerp(background, Color.White, 0.12f) else lerp(background, Color.Black, 0.08f)

private fun buildColorScheme(colors: ThemeColors, isDark: Boolean): ColorScheme {
    val primary = colors.primary
    val secondary = colors.secondary
    val accent = colors.accent
    val background = colors.background
    val surfaceBase = colors.surface
    val surfaceVariantBase = colors.surfaceVariant
    val outlineBase = colors.outline

    val primaryContainer = containerFor(primary, isDark)
    val secondaryContainer = containerFor(secondary, isDark)
    val accentContainer = containerFor(accent, isDark)

    // Surface tonal ramp: graduated steps between the user's surface color and
    // background/black/white depending on mode, so extended roles (used by
    // ModalBottomSheet, menus, nav bars, etc.) derive from user colors instead
    // of falling back to Material's baseline defaults.
    val surface = surfaceBase
    val surfaceDim: Color
    val surfaceBright: Color
    val surfaceContainerLowest: Color
    val surfaceContainerLow: Color
    val surfaceContainer: Color
    val surfaceContainerHigh: Color
    val surfaceContainerHighest: Color
    val inverseSurface: Color
    val inverseOnSurface: Color
    val inversePrimary: Color

    if (isDark) {
        surfaceDim = surface
        surfaceBright = lerp(surface, Color.White, 0.24f)
        surfaceContainerLowest = lerp(surface, Color.Black, 0.10f)
        surfaceContainerLow = lerp(surface, Color.White, 0.05f)
        surfaceContainer = lerp(surface, Color.White, 0.09f)
        surfaceContainerHigh = lerp(surface, Color.White, 0.14f)
        surfaceContainerHighest = lerp(surface, Color.White, 0.19f)
        inverseSurface = lerp(surface, Color.White, 0.85f)
        inverseOnSurface = onColorFor(inverseSurface)
        inversePrimary = containerFor(primary, false)
    } else {
        surfaceDim = lerp(surface, background, 0.4f)
        surfaceBright = lerp(surface, Color.White, 0.10f)
        surfaceContainerLowest = lerp(surface, Color.White, 0.05f)
        surfaceContainerLow = lerp(surface, background, 0.35f)
        surfaceContainer = lerp(surface, background, 0.55f)
        surfaceContainerHigh = lerp(surface, background, 0.75f)
        surfaceContainerHighest = lerp(surface, background, 0.9f)
        inverseSurface = lerp(surface, Color.Black, 0.85f)
        inverseOnSurface = onColorFor(inverseSurface)
        inversePrimary = containerFor(primary, true)
    }

    val onSurface = onColorFor(surface)
    val surfaceVariant = surfaceVariantBase
    val outline = outlineBase
    val outlineVariant = lerp(outline, surface, 0.5f)
    val scrim = Color(0xFF000000)

    val error = if (isDark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
    val onError = if (isDark) Color(0xFF690005) else Color.White
    val errorContainer = if (isDark) Color(0xFF93000A) else Color(0xFFFFDAD6)
    val onErrorContainer = if (isDark) Color(0xFFFFDAD6) else Color(0xFF410002)

    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onColorFor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onColorFor(primaryContainer),
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onColorFor(secondaryContainer),
            tertiary = accent,
            onTertiary = onColorFor(accent),
            tertiaryContainer = accentContainer,
            onTertiaryContainer = onColorFor(accentContainer),
            background = background,
            onBackground = onColorFor(background),
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onColorFor(surfaceVariant),
            surfaceTint = primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = scrim,
            surfaceBright = surfaceBright,
            surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onColorFor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onColorFor(primaryContainer),
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onColorFor(secondaryContainer),
            tertiary = accent,
            onTertiary = onColorFor(accent),
            tertiaryContainer = accentContainer,
            onTertiaryContainer = onColorFor(accentContainer),
            background = background,
            onBackground = onColorFor(background),
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onColorFor(surfaceVariant),
            surfaceTint = primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = scrim,
            surfaceBright = surfaceBright,
            surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
        )
    }
}

/**
 * Fixed semantic colors for the three macronutrients, used to color macro values in
 * list rows and the stats overview. These are intentionally independent of the user's
 * theme palette so protein/carbs/fat always read consistently. Shades are tuned per
 * light/dark for legibility; [fat] is chosen distinct from the calorie-state / error
 * reds elsewhere in the app so the two reds are not confused. Text labels always
 * accompany these colors, so color is never the only signal.
 */
data class MacroColors(
    val protein: Color,
    val carbs: Color,
    val fat: Color,
)

private val LightMacroColors = MacroColors(
    protein = Color(0xFF1E6BB8), // blue
    carbs = Color(0xFFB8860B),   // dark goldenrod, legible on light
    fat = Color(0xFFC0392B),     // brick red, distinct from error 0xFFBA1A1A
)

private val DarkMacroColors = MacroColors(
    protein = Color(0xFF74B4F0),
    carbs = Color(0xFFE6C34A),
    fat = Color(0xFFE5867E),
)

/** Theme-aware accessor for the semantic macro colors. */
@Composable
fun macroColors(isDark: Boolean = isSystemInDarkTheme()): MacroColors =
    if (isDark) DarkMacroColors else LightMacroColors

@Composable
fun VocalorieTheme(themeColors: ThemeColors, content: @Composable () -> Unit) {
    // `themeColors` is the only preference-derived input to `buildColorScheme`, and it is already
    // hoisted state that every save path updates, so no preference listener is needed here.
    val isDark = isSystemInDarkTheme()
    val colorScheme = remember(themeColors, isDark) { buildColorScheme(themeColors, isDark) }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
