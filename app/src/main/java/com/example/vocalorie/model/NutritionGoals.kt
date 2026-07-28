package com.example.vocalorie.model

/**
 * The user's daily nutrition targets that the day nutrition score is measured against.
 *
 * A calorie goal plus a macronutrient split (percentages of energy that sum to 100). Per-macro
 * gram targets are derived from these via [macroTargets] using Atwater factors (protein/carbs
 * 4 kcal/g, fat 9 kcal/g).
 */
data class NutritionGoals(
    val calorieGoalKcal: Int,
    val proteinPercent: Int,
    val carbsPercent: Int,
    val fatPercent: Int,
) {
    fun macroTargets(): MacroTargets {
        val cal = calorieGoalKcal.toDouble()
        return MacroTargets(
            proteinG = cal * proteinPercent / 100.0 / 4.0,
            carbsG = cal * carbsPercent / 100.0 / 4.0,
            fatG = cal * fatPercent / 100.0 / 9.0,
        )
    }

    companion object {
        /** Ships out-of-the-box so the score is live before the user configures anything. */
        val DEFAULT = NutritionGoals(calorieGoalKcal = 2400, proteinPercent = 30, carbsPercent = 40, fatPercent = 30)
    }
}

/** Derived daily gram targets for the three macros. */
data class MacroTargets(
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
