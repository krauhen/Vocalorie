package com.example.vocalorie.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.vocalorie.model.NutritionGoals
import java.math.BigDecimal
import java.math.MathContext

/**
 * The nutrition targets — daily calorie budget, macro split, base burn and step-burn rate.
 *
 * Split out of [ThemeSettingsStore], which is a *theme* store and had no business owning them.
 *
 * It deliberately reads the same `SharedPreferences` file under the same keys: the user has these
 * values on their device already, and a new file or a renamed key would silently reset them.
 */
class NutritionSettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBaseCaloriesBurned(): Int = prefs.getInt(KEY_BASE_CALORIES_BURNED, DEFAULT_BASE_CALORIES_BURNED)

    @Synchronized
    fun saveBaseCaloriesBurned(value: Int) {
        prefs.edit().putInt(KEY_BASE_CALORIES_BURNED, value).apply()
    }

    /** Seconds between day-score tip rotations; `0` means "no rotation, show the top tip only". */
    fun getTipRotationSeconds(): Int = prefs.getInt(KEY_TIP_ROTATION_SECONDS, DEFAULT_TIP_ROTATION_SECONDS)

    @Synchronized
    fun saveTipRotationSeconds(value: Int) {
        prefs.edit().putInt(KEY_TIP_ROTATION_SECONDS, value).apply()
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

    companion object {
        /**
         * The same file [ThemeSettingsStore] uses. Nutrition settings were stored there before they
         * were split out, and moving them would drop what the user already saved.
         */
        const val PREFS_NAME: String = ThemeSettingsStore.PREFS_NAME

        private const val KEY_BASE_CALORIES_BURNED = "base_calories_burned"

        /** Legacy `Float` slot, kept readable/writable so a downgrade does not lose the setting. */
        private const val KEY_KCAL_PER_STEP_LEGACY_FLOAT = "kcal_per_step"
        private const val KEY_KCAL_PER_STEP_DOUBLE = "kcal_per_step_double"
        private const val KEY_CALORIE_GOAL = "calorie_goal"
        private const val KEY_MACRO_PROTEIN_PERCENT = "macro_split_protein"
        private const val KEY_MACRO_CARBS_PERCENT = "macro_split_carbs"
        private const val KEY_MACRO_FAT_PERCENT = "macro_split_fat"

        private const val KEY_TIP_ROTATION_SECONDS = "tip_rotation_seconds"

        private const val DEFAULT_BASE_CALORIES_BURNED = 2400
        const val DEFAULT_TIP_ROTATION_SECONDS: Int = 5

        /** Accepted rotation intervals, alongside `0` for "off". Below 2 s a crossfade is unreadable. */
        val TIP_ROTATION_SECONDS_RANGE: IntRange = 2..60
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
}
