package com.example.vocalorie.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The colour setters after they were switched to the store's own `saveColor` helper.
 *
 * Six of them used to inline `prefs.edit()`. The helper must write the same key with the same ARGB
 * value — the user's palette is already on their device, and a changed key would reset it.
 */
class ThemeSettingsStoreColorsTest {

    @Test
    fun everyColourSetterWritesItsDocumentedKey() {
        val prefs = InMemorySharedPreferences()
        val store = ThemeSettingsStore(testContext(prefs))

        store.savePrimary(Color(0xFF000001))
        store.saveSecondary(Color(0xFF000002))
        store.saveAccent(Color(0xFF000003))
        store.saveBackground(Color(0xFF000004))
        store.saveSurface(Color(0xFF000005))
        store.saveSurfaceVariant(Color(0xFF000006))
        store.saveOutline(Color(0xFF000007))
        store.saveActivityPrimary(Color(0xFF000008))
        store.saveActivitySecondary(Color(0xFF000009))
        store.saveActivityAccent(Color(0xFF00000A))
        store.saveActivityOutline(Color(0xFF00000B))

        assertEquals(
            mapOf(
                "theme_primary" to Color(0xFF000001).toArgb(),
                "theme_secondary" to Color(0xFF000002).toArgb(),
                "theme_accent" to Color(0xFF000003).toArgb(),
                "theme_background" to Color(0xFF000004).toArgb(),
                "theme_surface" to Color(0xFF000005).toArgb(),
                "theme_surface_variant" to Color(0xFF000006).toArgb(),
                "theme_outline" to Color(0xFF000007).toArgb(),
                "activity_theme_primary" to Color(0xFF000008).toArgb(),
                "activity_theme_secondary" to Color(0xFF000009).toArgb(),
                "activity_theme_accent" to Color(0xFF00000A).toArgb(),
                "activity_theme_outline" to Color(0xFF00000B).toArgb(),
            ),
            prefs.all,
        )
    }

    @Test
    fun theMealSchemeRoundTripsThroughEverySetter() {
        val store = ThemeSettingsStore(testContext(InMemorySharedPreferences()))
        val expected = ThemeColors(
            primary = Color(0xFF112233),
            secondary = Color(0xFF223344),
            accent = Color(0xFF334455),
            background = Color(0xFF445566),
            surface = Color(0xFF556677),
            surfaceVariant = Color(0xFF667788),
            outline = Color(0xFF778899),
        )

        store.savePrimary(expected.primary)
        store.saveSecondary(expected.secondary)
        store.saveAccent(expected.accent)
        store.saveBackground(expected.background)
        store.saveSurface(expected.surface)
        store.saveSurfaceVariant(expected.surfaceVariant)
        store.saveOutline(expected.outline)

        assertEquals(expected, store.get())
    }

    @Test
    fun theActivitySchemeRoundTripsAndKeepsSharingTheAppearanceSurfaces() {
        val store = ThemeSettingsStore(testContext(InMemorySharedPreferences()))
        val background = Color(0xFF445566)
        val surface = Color(0xFF556677)
        val surfaceVariant = Color(0xFF667788)

        store.saveBackground(background)
        store.saveSurface(surface)
        store.saveSurfaceVariant(surfaceVariant)
        store.saveActivityPrimary(Color(0xFF0F172A))
        store.saveActivitySecondary(Color(0xFF2563EB))
        store.saveActivityAccent(Color(0xFF60A5FA))
        store.saveActivityOutline(Color(0xFF334155))

        val activity = store.getActivityColors()
        assertEquals(Color(0xFF0F172A), activity.primary)
        assertEquals(Color(0xFF2563EB), activity.secondary)
        assertEquals(Color(0xFF60A5FA), activity.accent)
        assertEquals(Color(0xFF334155), activity.outline)
        // Background/surface/surface-variant are one shared "Appearance" set, not per-tab.
        assertEquals(background, activity.background)
        assertEquals(surface, activity.surface)
        assertEquals(surfaceVariant, activity.surfaceVariant)
    }

    @Test
    fun defaultsAreUnchangedWhenNothingIsStored() {
        val store = ThemeSettingsStore(testContext(InMemorySharedPreferences()))

        assertEquals(
            ThemeColors(
                primary = Color(0xFFF77605),
                secondary = Color(0xFFE5E5E5),
                accent = Color(0xFFFFA000),
                background = Color(0xFFFAFAFA),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFE5E5E5),
                outline = Color(0xFF71717A),
            ),
            store.get(),
        )
        assertEquals(
            ThemeColors(
                primary = Color(0xFF0F172A),
                secondary = Color(0xFF2563EB),
                accent = Color(0xFF60A5FA),
                background = Color(0xFFFAFAFA),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFE5E5E5),
                outline = Color(0xFF334155),
            ),
            store.getActivityColors(),
        )
    }
}
