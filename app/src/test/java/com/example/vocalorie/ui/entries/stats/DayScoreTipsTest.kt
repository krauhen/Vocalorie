package com.example.vocalorie.ui.entries.stats

import com.example.vocalorie.model.NutritionGoals
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranking, catalogue, late-day gating and reply validation for the day-score tips.
 *
 * Targets throughout are the defaults: 2400 kcal, 180 g protein, 240 g carbs, 80 g fat.
 */
class DayScoreTipsTest {

    private val midday = LocalTime.of(12, 0)
    private val evening = LocalTime.of(18, 0)
    private val lateNight = LocalTime.of(23, 0)

    private fun totals(
        calories: Double = 2400.0,
        protein: Double = 180.0,
        carbs: Double = 240.0,
        fat: Double = 80.0,
        saturatedFat: Double = 0.0,
        sugar: Double = 0.0,
        salt: Double = 0.0,
    ) = DailyNutritionTotals(calories, protein, carbs, fat, saturatedFat, sugar, salt)

    private fun kinds(
        totals: DailyNutritionTotals,
        hasLoggedActivity: Boolean = true,
        activityBurnedKcal: Double = 0.0,
        localTime: LocalTime = midday,
    ): List<DayScoreTipKind> =
        dayScoreTips(totals, NutritionGoals.DEFAULT, activityBurnedKcal, hasLoggedActivity, localTime).map { it.kind }

    @Test
    fun `a 60-point calorie loss outranks a 20-point fat loss`() {
        // calories r = 1.17 -> adherence 40 (rank 24); fat r = 1.28 -> adherence 80 (rank 3).
        val result = kinds(totals(calories = 2808.0, fat = 102.4))

        assertEquals(listOf(DayScoreTipKind.CALORIES_OVER, DayScoreTipKind.FAT_OVER), result)
    }

    @Test
    fun `an on-target component emits no tip`() {
        val result = kinds(totals(protein = 100.0))

        assertEquals(listOf(DayScoreTipKind.PROTEIN_UNDER), result)
    }

    @Test
    fun `a carbs gap outranks a doubled sugar overage`() {
        // carbs r = 0.4 -> adherence 0 (rank 15); sugar at 2x its 60 g limit docks the full 10.
        val result = kinds(totals(carbs = 96.0, sugar = 120.0), localTime = evening)

        assertEquals(listOf(DayScoreTipKind.CARBS_UNDER, DayScoreTipKind.SUGAR_OVER), result)
    }

    @Test
    fun `a perfect day yields no tips`() {
        assertEquals(emptyList<DayScoreTipKind>(), kinds(totals()))
    }

    @Test
    fun `a day with nothing logged yields no tips`() {
        assertEquals(
            emptyList<DayScoreTipKind>(),
            kinds(totals(calories = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0), hasLoggedActivity = false),
        )
    }

    @Test
    fun `the activity tip appears only when over budget with no activity logged`() {
        val over = totals(calories = 3000.0)

        assertTrue(DayScoreTipKind.NO_ACTIVITY_WHILE_OVER in kinds(over, hasLoggedActivity = false))
        assertFalse(DayScoreTipKind.NO_ACTIVITY_WHILE_OVER in kinds(over, hasLoggedActivity = true))
        assertFalse(DayScoreTipKind.NO_ACTIVITY_WHILE_OVER in kinds(totals(), hasLoggedActivity = false))
    }

    @Test
    fun `the activity tip sits directly below the calorie tip`() {
        val result = kinds(totals(calories = 2808.0, fat = 102.4), hasLoggedActivity = false)

        assertEquals(
            listOf(
                DayScoreTipKind.CALORIES_OVER,
                DayScoreTipKind.NO_ACTIVITY_WHILE_OVER,
                DayScoreTipKind.FAT_OVER,
            ),
            result,
        )
    }

    @Test
    fun `late at night the eat-more tips drop but the over-budget one stays`() {
        val day = totals(calories = 3200.0, protein = 100.0)

        assertEquals(listOf(DayScoreTipKind.CALORIES_FAR_OVER), kinds(day, localTime = lateNight))
    }

    @Test
    fun `in the evening the protein tip still shows`() {
        val day = totals(calories = 3200.0, protein = 100.0)

        assertEquals(
            listOf(DayScoreTipKind.CALORIES_FAR_OVER, DayScoreTipKind.PROTEIN_UNDER),
            kinds(day, localTime = evening),
        )
    }

    @Test
    fun `late at night an all-eat-more day yields no tips`() {
        val day = totals(calories = 1800.0, protein = 100.0, carbs = 100.0, fat = 40.0)

        assertEquals(emptyList<DayScoreTipKind>(), kinds(day, localTime = lateNight))
    }

    @Test
    fun `the catalogue covers every kind with 5 to 10 words`() {
        assertEquals(DayScoreTipKind.entries.toSet(), DAY_SCORE_TIP_TEXTS.keys)
        DAY_SCORE_TIP_TEXTS.forEach { (kind, text) ->
            val words = text.split(" ").count { token -> token.any(Char::isLetterOrDigit) }
            assertTrue("$kind is $words words: $text", words in 5..10)
        }
    }

    @Test
    fun `the catalogue wording matches the agreed copy`() {
        assertEquals(
            "You're well over budget — consider stopping for today.",
            DAY_SCORE_TIP_TEXTS.getValue(DayScoreTipKind.CALORIES_FAR_OVER),
        )
        assertEquals(
            "Over budget — log some sport to offset it.",
            DAY_SCORE_TIP_TEXTS.getValue(DayScoreTipKind.NO_ACTIVITY_WHILE_OVER),
        )
    }

    @Test
    fun `there is no protein-over tip because the score does not penalise it`() {
        assertFalse(DayScoreTipKind.entries.any { it.name == "PROTEIN_OVER" })
        assertEquals(emptyList<DayScoreTipKind>(), kinds(totals(protein = 400.0)))
    }

    @Test
    fun `rewording is accepted only at the same count with every entry 5 to 10 words`() {
        val ruleTips = listOf(
            DayScoreTip(DayScoreTipKind.CALORIES_OVER, "Ease off — you're over your calorie budget today."),
            DayScoreTip(DayScoreTipKind.PROTEIN_UNDER, "Protein is short — add a protein-heavy meal."),
            DayScoreTip(DayScoreTipKind.SALT_OVER, "Salt is high — go easy on salty foods."),
        )

        val valid = listOf(
            "Slow down, you have passed today's calorie budget.",
            "Short on protein — eat something protein rich.",
            "Too much salt — pick less salty food.",
        )
        assertEquals(valid, validateRewordedTips(ruleTips, valid).map { it.text })
        assertEquals(ruleTips.map { it.kind }, validateRewordedTips(ruleTips, valid).map { it.kind })

        assertEquals(ruleTips, validateRewordedTips(ruleTips, valid.take(2)))
        assertEquals(
            ruleTips,
            validateRewordedTips(
                ruleTips,
                listOf(valid[0], valid[1], "This particular reply entry runs to a full twelve separate words here"),
            ),
        )
        assertEquals(ruleTips, validateRewordedTips(ruleTips, listOf(valid[0], valid[1], "Too few words")))
    }
}
