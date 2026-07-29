package com.example.vocalorie.ui.settings

import androidx.compose.ui.graphics.Color
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.settings.ThemeColors
import com.example.vocalorie.settings.ToolSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The settings screen used to receive eleven interchangeable `(Color) -> Unit` callbacks, so a
 * transposed argument would have silently restyled the app with no compiler complaint. Now that a
 * named [ThemeColorSlot] carries that meaning, this pins the mapping in both directions: which
 * palette entry a slot *reads*, and which palette a slot belongs to.
 */
class SettingsUiStateTest {

    /** Every channel a distinct value, so any transposition changes the assertion's result. */
    private val mealColors = ThemeColors(
        primary = Color(0xFF000001),
        secondary = Color(0xFF000002),
        accent = Color(0xFF000003),
        background = Color(0xFF000004),
        surface = Color(0xFF000005),
        surfaceVariant = Color(0xFF000006),
        outline = Color(0xFF000007),
    )

    private val activityColors = ThemeColors(
        primary = Color(0xFF000011),
        secondary = Color(0xFF000012),
        accent = Color(0xFF000013),
        background = Color(0xFF000014),
        surface = Color(0xFF000015),
        surfaceVariant = Color(0xFF000016),
        outline = Color(0xFF000017),
    )

    private val state = SettingsUiState(
        mealColors = mealColors,
        activityColors = activityColors,
        baseCaloriesBurned = 2000,
        kcalPerStep = 0.03,
        nutritionGoals = NutritionGoals(2200, 30, 40, 30),
        savedKeyLabel = null,
        runtimeApiKey = "",
        braveKeyLabel = null,
        toolSettings = ToolSettings(),
        message = null,
        enabled = true,
    )

    @Test
    fun eachSlotReadsItsOwnPaletteEntry() {
        assertEquals(mealColors.background, state.color(ThemeColorSlot.BACKGROUND))
        assertEquals(mealColors.surface, state.color(ThemeColorSlot.SURFACE))
        assertEquals(mealColors.surfaceVariant, state.color(ThemeColorSlot.SURFACE_VARIANT))
        assertEquals(mealColors.primary, state.color(ThemeColorSlot.MEAL_PRIMARY))
        assertEquals(mealColors.secondary, state.color(ThemeColorSlot.MEAL_SECONDARY))
        assertEquals(mealColors.accent, state.color(ThemeColorSlot.MEAL_ACCENT))
        assertEquals(mealColors.outline, state.color(ThemeColorSlot.MEAL_OUTLINE))
        assertEquals(activityColors.primary, state.color(ThemeColorSlot.ACTIVITY_PRIMARY))
        assertEquals(activityColors.secondary, state.color(ThemeColorSlot.ACTIVITY_SECONDARY))
        assertEquals(activityColors.accent, state.color(ThemeColorSlot.ACTIVITY_ACCENT))
        assertEquals(activityColors.outline, state.color(ThemeColorSlot.ACTIVITY_OUTLINE))
    }

    /** Guards the fixture itself: without distinct values above, a transposition would still pass. */
    @Test
    fun noTwoSlotsResolveToTheSameColor() {
        val collisions = ThemeColorSlot.values().groupBy { state.color(it) }.filterValues { it.size > 1 }

        assertEquals("slots sharing a colour hide transpositions: $collisions", emptyMap<Color, List<ThemeColorSlot>>(), collisions)
    }

    /**
     * The three shared surface slots are persisted in the meal palette. Swapping which palette the
     * state carries must move them, proving they do not read the activity palette by accident.
     */
    @Test
    fun sharedSurfaceSlotsFollowTheMealPalette() {
        val swapped = state.copy(mealColors = activityColors)

        assertEquals(activityColors.background, swapped.color(ThemeColorSlot.BACKGROUND))
        assertEquals(activityColors.surface, swapped.color(ThemeColorSlot.SURFACE))
        assertEquals(activityColors.surfaceVariant, swapped.color(ThemeColorSlot.SURFACE_VARIANT))
    }
}
