package com.example.vocalorie.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.vocalorie.model.NutritionGoals
import java.math.BigDecimal
import java.math.MathContext

data class ThemeColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
)

class ThemeSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): ThemeColors = ThemeColors(
        primary = readColor(KEY_PRIMARY, DEFAULT_PRIMARY),
        secondary = readColor(KEY_SECONDARY, DEFAULT_SECONDARY),
        accent = readColor(KEY_ACCENT, DEFAULT_ACCENT),
        background = readColor(KEY_BACKGROUND, DEFAULT_BACKGROUND),
        surface = readColor(KEY_SURFACE, DEFAULT_SURFACE),
        surfaceVariant = readColor(KEY_SURFACE_VARIANT, DEFAULT_SURFACE_VARIANT),
        outline = readColor(KEY_OUTLINE, DEFAULT_OUTLINE),
    )

    // Background/surface/surface-variant are shared with the meal scheme (single "Appearance" set).
    fun getActivityColors(): ThemeColors = ThemeColors(
        primary = readColor(KEY_ACTIVITY_PRIMARY, DEFAULT_ACTIVITY_PRIMARY),
        secondary = readColor(KEY_ACTIVITY_SECONDARY, DEFAULT_ACTIVITY_SECONDARY),
        accent = readColor(KEY_ACTIVITY_ACCENT, DEFAULT_ACTIVITY_ACCENT),
        background = readColor(KEY_BACKGROUND, DEFAULT_BACKGROUND),
        surface = readColor(KEY_SURFACE, DEFAULT_SURFACE),
        surfaceVariant = readColor(KEY_SURFACE_VARIANT, DEFAULT_SURFACE_VARIANT),
        outline = readColor(KEY_ACTIVITY_OUTLINE, DEFAULT_ACTIVITY_OUTLINE),
    )

    @Synchronized
    fun savePrimary(color: Color) {
        prefs.edit().putInt(KEY_PRIMARY, color.toArgb()).apply()
    }

    @Synchronized
    fun saveSecondary(color: Color) {
        prefs.edit().putInt(KEY_SECONDARY, color.toArgb()).apply()
    }

    @Synchronized
    fun saveAccent(color: Color) {
        prefs.edit().putInt(KEY_ACCENT, color.toArgb()).apply()
    }

    @Synchronized
    fun saveBackground(color: Color) {
        prefs.edit().putInt(KEY_BACKGROUND, color.toArgb()).apply()
    }

    @Synchronized
    fun saveSurface(color: Color) {
        prefs.edit().putInt(KEY_SURFACE, color.toArgb()).apply()
    }

    @Synchronized
    fun saveSurfaceVariant(color: Color) {
        prefs.edit().putInt(KEY_SURFACE_VARIANT, color.toArgb()).apply()
    }

    @Synchronized
    fun saveOutline(color: Color) {
        saveColor(KEY_OUTLINE, color)
    }

    @Synchronized
    fun saveActivityPrimary(color: Color) {
        saveColor(KEY_ACTIVITY_PRIMARY, color)
    }

    @Synchronized
    fun saveActivitySecondary(color: Color) {
        saveColor(KEY_ACTIVITY_SECONDARY, color)
    }

    @Synchronized
    fun saveActivityAccent(color: Color) {
        saveColor(KEY_ACTIVITY_ACCENT, color)
    }

    @Synchronized
    fun saveActivityOutline(color: Color) {
        saveColor(KEY_ACTIVITY_OUTLINE, color)
    }

    fun getBaseCaloriesBurned(): Int = prefs.getInt(KEY_BASE_CALORIES_BURNED, DEFAULT_BASE_CALORIES_BURNED)

    @Synchronized
    fun saveBaseCaloriesBurned(value: Int) {
        prefs.edit().putInt(KEY_BASE_CALORIES_BURNED, value).apply()
    }

    /**
     * Reads the step-burn rate, migrating a legacy `Float` value to `Double` on first read.
     *
     * The legacy `Float` round-trip turned an entered `30` per 1,000 steps into
     * `29.999999329447746`, so a value written by an older build is cleaned once and rewritten
     * under the `Double` key. The legacy key is deliberately left in place so a downgrade keeps
     * working.
     */
    @Synchronized
    fun getKcalPerStep(): Double {
        if (prefs.contains(KEY_KCAL_PER_STEP_DOUBLE)) {
            return Double.fromBits(prefs.getLong(KEY_KCAL_PER_STEP_DOUBLE, DEFAULT_KCAL_PER_STEP.toRawBits()))
        }
        if (!prefs.contains(KEY_KCAL_PER_STEP_LEGACY_FLOAT)) return DEFAULT_KCAL_PER_STEP

        val migrated = cleanFloatPrecision(prefs.getFloat(KEY_KCAL_PER_STEP_LEGACY_FLOAT, DEFAULT_KCAL_PER_STEP.toFloat()))
        prefs.edit().putLong(KEY_KCAL_PER_STEP_DOUBLE, migrated.toRawBits()).apply()
        return migrated
    }

    @Synchronized
    fun saveKcalPerStep(value: Double) {
        prefs.edit()
            .putLong(KEY_KCAL_PER_STEP_DOUBLE, value.toRawBits())
            // Keep the legacy key in sync so downgrading to an older build keeps the setting.
            .putFloat(KEY_KCAL_PER_STEP_LEGACY_FLOAT, value.toFloat())
            .apply()
    }

    /** The daily calorie + macro-split targets used by the day nutrition score. */
    fun getNutritionGoals(): NutritionGoals = NutritionGoals(
        calorieGoalKcal = prefs.getInt(KEY_CALORIE_GOAL, NutritionGoals.DEFAULT.calorieGoalKcal),
        proteinPercent = prefs.getInt(KEY_MACRO_PROTEIN_PERCENT, NutritionGoals.DEFAULT.proteinPercent),
        carbsPercent = prefs.getInt(KEY_MACRO_CARBS_PERCENT, NutritionGoals.DEFAULT.carbsPercent),
        fatPercent = prefs.getInt(KEY_MACRO_FAT_PERCENT, NutritionGoals.DEFAULT.fatPercent),
    )

    @Synchronized
    fun saveNutritionGoals(goals: NutritionGoals) {
        prefs.edit()
            .putInt(KEY_CALORIE_GOAL, goals.calorieGoalKcal)
            .putInt(KEY_MACRO_PROTEIN_PERCENT, goals.proteinPercent)
            .putInt(KEY_MACRO_CARBS_PERCENT, goals.carbsPercent)
            .putInt(KEY_MACRO_FAT_PERCENT, goals.fatPercent)
            .apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val PREFS_NAME = "theme_settings_store"
        private const val KEY_PRIMARY = "theme_primary"
        private const val KEY_SECONDARY = "theme_secondary"
        private const val KEY_ACCENT = "theme_accent"
        private const val KEY_BACKGROUND = "theme_background"
        private const val KEY_SURFACE = "theme_surface"
        private const val KEY_SURFACE_VARIANT = "theme_surface_variant"
        private const val KEY_OUTLINE = "theme_outline"
        private const val KEY_ACTIVITY_PRIMARY = "activity_theme_primary"
        private const val KEY_ACTIVITY_SECONDARY = "activity_theme_secondary"
        private const val KEY_ACTIVITY_ACCENT = "activity_theme_accent"
        private const val KEY_ACTIVITY_OUTLINE = "activity_theme_outline"
        private const val KEY_BASE_CALORIES_BURNED = "base_calories_burned"

        /** Legacy `Float` slot, kept readable/writable so a downgrade does not lose the setting. */
        private const val KEY_KCAL_PER_STEP_LEGACY_FLOAT = "kcal_per_step"
        private const val KEY_KCAL_PER_STEP_DOUBLE = "kcal_per_step_double"
        private const val KEY_CALORIE_GOAL = "calorie_goal"
        private const val KEY_MACRO_PROTEIN_PERCENT = "macro_split_protein"
        private const val KEY_MACRO_CARBS_PERCENT = "macro_split_carbs"
        private const val KEY_MACRO_FAT_PERCENT = "macro_split_fat"

        // Defaults mirror the app's original hardcoded light color scheme.
        private const val DEFAULT_PRIMARY = 0xFFF77605.toInt()
        private const val DEFAULT_SECONDARY = 0xFFE5E5E5.toInt()
        private const val DEFAULT_ACCENT = 0xFFFFA000.toInt()
        private const val DEFAULT_BACKGROUND = 0xFFFAFAFA.toInt()
        private const val DEFAULT_SURFACE = 0xFFFFFFFF.toInt()
        private const val DEFAULT_SURFACE_VARIANT = 0xFFE5E5E5.toInt()
        private const val DEFAULT_OUTLINE = 0xFF71717A.toInt()
        private const val DEFAULT_ACTIVITY_PRIMARY = 0xFF0F172A.toInt()
        private const val DEFAULT_ACTIVITY_SECONDARY = 0xFF2563EB.toInt()
        private const val DEFAULT_ACTIVITY_ACCENT = 0xFF60A5FA.toInt()
        private const val DEFAULT_ACTIVITY_OUTLINE = 0xFF334155.toInt()
        private const val DEFAULT_BASE_CALORIES_BURNED = 2400
        // 35 kcal per 1,000 steps (mid of the common 30–40 range).
        private const val DEFAULT_KCAL_PER_STEP = 0.035

        /** `Float` carries about seven significant decimal digits; the rest is conversion noise. */
        private const val FLOAT_SIGNIFICANT_DIGITS = 7

        /**
         * Rounds a value that survived a `Float` round-trip back to `Float`'s real precision, so a
         * stored `0.03f` reads as exactly `0.03` instead of `0.029999999329447746`.
         */
        internal fun cleanFloatPrecision(value: Float): Double =
            BigDecimal(value.toDouble()).round(MathContext(FLOAT_SIGNIFICANT_DIGITS)).toDouble()
    }

    private fun readColor(key: String, defaultArgb: Int): Color = Color(prefs.getInt(key, defaultArgb))

    private fun saveColor(key: String, color: Color) {
        prefs.edit().putInt(key, color.toArgb()).apply()
    }
}
