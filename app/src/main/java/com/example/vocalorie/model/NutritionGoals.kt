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

        /**
         * The accepted whole-percent range for each macro share.
         *
         * The user enters protein and carbs; fat is whatever is left of 100. Requiring all three
         * to land in this range is the same rule as "protein, carbs and the derived fat share are
         * each non-negative", because three non-negative shares summing to 100 can none of them
         * exceed 100.
         */
        val percentRange: IntRange = 0..100

        /**
         * Parses the three Settings inputs into goals, deriving the fat share from the other two.
         *
         * Pure: no clock, no storage, no Android. Whitespace is trimmed, anything non-integral is a
         * validation failure, and the returned failure carries the message shown to the user.
         */
        fun parse(
            calorieGoalInput: String,
            proteinPercentInput: String,
            carbsPercentInput: String,
        ): NutritionGoalsParseResult {
            val goal = calorieGoalInput.trim().toIntOrNull()
            val protein = proteinPercentInput.trim().toIntOrNull()
            val carbs = carbsPercentInput.trim().toIntOrNull()
            val fat = if (protein != null && carbs != null) 100 - protein - carbs else null
            return when {
                goal == null || goal <= 0 -> NutritionGoalsParseResult.Invalid(INVALID_CALORIE_GOAL_MESSAGE)
                protein == null || carbs == null || fat == null ||
                    protein !in percentRange || carbs !in percentRange || fat !in percentRange ->
                    NutritionGoalsParseResult.Invalid(INVALID_MACRO_PERCENT_MESSAGE)
                else -> NutritionGoalsParseResult.Parsed(
                    NutritionGoals(calorieGoalKcal = goal, proteinPercent = protein, carbsPercent = carbs, fatPercent = fat),
                )
            }
        }
    }
}

/** Outcome of [NutritionGoals.parse]: either goals to save, or the message to show the user. */
sealed interface NutritionGoalsParseResult {
    data class Parsed(val goals: NutritionGoals) : NutritionGoalsParseResult

    data class Invalid(val message: String) : NutritionGoalsParseResult
}

private const val INVALID_CALORIE_GOAL_MESSAGE = "Enter a whole number greater than 0 for the daily calorie goal."
private const val INVALID_MACRO_PERCENT_MESSAGE =
    "Protein and carbs percentages must be whole numbers that leave a non-negative fat share."

/** Derived daily gram targets for the three macros. */
data class MacroTargets(
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
