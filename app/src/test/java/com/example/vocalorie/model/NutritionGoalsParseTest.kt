package com.example.vocalorie.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the nutrition-goal rule extracted out of the Settings save lambda: the fat share is
 * derived, not entered, and both rejection messages are the ones the user sees.
 */
class NutritionGoalsParseTest {

    private val calorieGoalMessage = "Enter a whole number greater than 0 for the daily calorie goal."
    private val macroPercentMessage =
        "Protein and carbs percentages must be whole numbers that leave a non-negative fat share."

    private fun parsed(calorieGoal: String, protein: String, carbs: String): NutritionGoals {
        val result = NutritionGoals.parse(calorieGoal, protein, carbs)
        assertTrue("expected $calorieGoal/$protein/$carbs to parse, got $result", result is NutritionGoalsParseResult.Parsed)
        return (result as NutritionGoalsParseResult.Parsed).goals
    }

    private fun invalidMessage(calorieGoal: String, protein: String, carbs: String): String {
        val result = NutritionGoals.parse(calorieGoal, protein, carbs)
        assertTrue("expected $calorieGoal/$protein/$carbs to be rejected, got $result", result is NutritionGoalsParseResult.Invalid)
        return (result as NutritionGoalsParseResult.Invalid).message
    }

    @Test
    fun validInputDerivesTheFatShareFromProteinAndCarbs() {
        assertEquals(
            NutritionGoals(calorieGoalKcal = 2400, proteinPercent = 30, carbsPercent = 40, fatPercent = 30),
            parsed("2400", "30", "40"),
        )
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals(
            NutritionGoals(calorieGoalKcal = 1800, proteinPercent = 25, carbsPercent = 45, fatPercent = 30),
            parsed("  1800 ", " 25", "45  "),
        )
    }

    @Test
    fun zeroPercentSharesAreAccepted() {
        assertEquals(
            NutritionGoals(calorieGoalKcal = 2000, proteinPercent = 0, carbsPercent = 0, fatPercent = 100),
            parsed("2000", "0", "0"),
        )
    }

    @Test
    fun proteinAndCarbsSummingToOneHundredLeaveNoFatShare() {
        assertEquals(
            NutritionGoals(calorieGoalKcal = 2000, proteinPercent = 40, carbsPercent = 60, fatPercent = 0),
            parsed("2000", "40", "60"),
        )
    }

    @Test
    fun aSingleKilocalorieGoalIsTheSmallestAcceptedGoal() {
        assertEquals(1, parsed("1", "30", "40").calorieGoalKcal)
    }

    @Test
    fun blankCalorieGoalIsRejected() {
        assertEquals(calorieGoalMessage, invalidMessage("", "30", "40"))
        assertEquals(calorieGoalMessage, invalidMessage("   ", "30", "40"))
    }

    @Test
    fun nonNumericCalorieGoalIsRejected() {
        assertEquals(calorieGoalMessage, invalidMessage("two thousand", "30", "40"))
    }

    @Test
    fun fractionalCalorieGoalIsRejectedBecauseTheGoalIsAWholeNumber() {
        assertEquals(calorieGoalMessage, invalidMessage("2400.5", "30", "40"))
    }

    @Test
    fun zeroOrNegativeCalorieGoalIsRejected() {
        assertEquals(calorieGoalMessage, invalidMessage("0", "30", "40"))
        assertEquals(calorieGoalMessage, invalidMessage("-1", "30", "40"))
    }

    @Test
    fun anInvalidCalorieGoalIsReportedBeforeAnInvalidPercentage() {
        // Both are wrong; the calorie-goal message is the one the user gets.
        assertEquals(calorieGoalMessage, invalidMessage("0", "", ""))
    }

    @Test
    fun blankOrNonNumericPercentagesAreRejected() {
        assertEquals(macroPercentMessage, invalidMessage("2400", "", "40"))
        assertEquals(macroPercentMessage, invalidMessage("2400", "30", ""))
        assertEquals(macroPercentMessage, invalidMessage("2400", "thirty", "40"))
    }

    @Test
    fun fractionalPercentagesAreRejected() {
        assertEquals(macroPercentMessage, invalidMessage("2400", "30.5", "40"))
        assertEquals(macroPercentMessage, invalidMessage("2400", "30", "40,5"))
    }

    @Test
    fun negativePercentagesAreRejected() {
        assertEquals(macroPercentMessage, invalidMessage("2400", "-1", "40"))
        assertEquals(macroPercentMessage, invalidMessage("2400", "30", "-1"))
    }

    @Test
    fun percentagesSummingAboveOneHundredAreRejectedBecauseTheFatShareWouldBeNegative() {
        assertEquals(macroPercentMessage, invalidMessage("2400", "60", "41"))
        assertEquals(macroPercentMessage, invalidMessage("2400", "101", "0"))
    }

    @Test
    fun everyAcceptedShareLiesInThePercentRange() {
        val goals = parsed("2400", "30", "40")

        assertEquals(0..100, NutritionGoals.percentRange)
        listOf(goals.proteinPercent, goals.carbsPercent, goals.fatPercent).forEach { share ->
            assertTrue("$share should be inside ${NutritionGoals.percentRange}", share in NutritionGoals.percentRange)
        }
    }

    @Test
    fun theThreeAcceptedSharesAlwaysSumToOneHundred() {
        listOf("0" to "0", "0" to "100", "100" to "0", "30" to "40", "50" to "25").forEach { (protein, carbs) ->
            val goals = parsed("2400", protein, carbs)
            assertEquals(100, goals.proteinPercent + goals.carbsPercent + goals.fatPercent)
        }
    }
}
