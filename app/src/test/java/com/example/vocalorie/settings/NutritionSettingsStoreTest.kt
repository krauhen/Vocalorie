package com.example.vocalorie.settings

import com.example.vocalorie.model.NutritionGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The nutrition targets after being split out of [ThemeSettingsStore].
 *
 * The point of these is the storage contract, not the arithmetic: the user has these values on their
 * device already, so a renamed key or a new preference file would silently reset their settings.
 */
class NutritionSettingsStoreTest {

    @Test
    fun itReadsTheSamePreferenceFileTheThemeStoreUses() {
        assertEquals("theme_settings_store", NutritionSettingsStore.PREFS_NAME)
        assertEquals(ThemeSettingsStore.PREFS_NAME, NutritionSettingsStore.PREFS_NAME)
    }

    @Test
    fun everyStoredKeyIsUnchangedFromTheThemeStoreVersion() {
        val prefs = InMemorySharedPreferences()
        val store = NutritionSettingsStore(testContext(prefs))

        store.saveBaseCaloriesBurned(2100)
        store.saveKcalPerStep(0.03)
        store.saveNutritionGoals(NutritionGoals(calorieGoalKcal = 1900, proteinPercent = 30, carbsPercent = 40, fatPercent = 30))

        assertEquals(
            setOf(
                "base_calories_burned",
                "kcal_per_step",
                "kcal_per_step_double",
                "calorie_goal",
                "macro_split_protein",
                "macro_split_carbs",
                "macro_split_fat",
            ),
            prefs.all.keys,
        )
    }

    @Test
    fun everySettingRoundTrips() {
        val store = NutritionSettingsStore(testContext(InMemorySharedPreferences()))
        val goals = NutritionGoals(calorieGoalKcal = 1900, proteinPercent = 30, carbsPercent = 40, fatPercent = 30)

        store.saveBaseCaloriesBurned(2100)
        store.saveKcalPerStep(30 / 1000.0)
        store.saveNutritionGoals(goals)

        assertEquals(2100, store.getBaseCaloriesBurned())
        assertEquals(0.03, store.getKcalPerStep(), 0.0)
        assertEquals(goals, store.getNutritionGoals())
    }

    @Test
    fun unsetValuesFallBackToTheDocumentedDefaults() {
        val store = NutritionSettingsStore(testContext(InMemorySharedPreferences()))

        assertEquals(2400, store.getBaseCaloriesBurned())
        assertEquals(0.035, store.getKcalPerStep(), 0.0)
        assertEquals(NutritionGoals.DEFAULT, store.getNutritionGoals())
    }

    @Test
    fun theLegacyFloatStepBurnStillMigratesAfterTheSplit() {
        val prefs = InMemorySharedPreferences()
        // What an older build left behind for an entered "30": 0.03f.
        prefs.edit().putFloat("kcal_per_step", 0.03f).apply()
        val store = NutritionSettingsStore(testContext(prefs))

        assertEquals(30.0, store.getKcalPerStep() * 1000, 0.0)
        assertTrue(prefs.contains("kcal_per_step_double"))
        // A downgrade must still find its Float value.
        assertTrue(prefs.contains("kcal_per_step"))
    }

    @Test
    fun aValueWrittenThroughEitherStoreIsReadableThroughTheOther() {
        // The theme store still forwards for `MealCaptureScreen`; both must see one set of values.
        val prefs = InMemorySharedPreferences()
        val themeStore = ThemeSettingsStore(testContext(prefs))
        val nutritionStore = NutritionSettingsStore(testContext(prefs))

        themeStore.saveBaseCaloriesBurned(2100)
        themeStore.saveKcalPerStep(0.03)
        assertEquals(2100, nutritionStore.getBaseCaloriesBurned())
        assertEquals(0.03, nutritionStore.getKcalPerStep(), 0.0)

        nutritionStore.saveBaseCaloriesBurned(1800)
        assertEquals(1800, themeStore.getBaseCaloriesBurned())
    }
}
