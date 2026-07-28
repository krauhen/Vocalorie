package com.example.vocalorie.ui.entries.stats

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.model.SavedMeal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealStatsCalculatorTest {
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val now: Instant = LocalDateTime.of(2026, 7, 9, 12, 0).atZone(zone).toInstant()

    // 2026-07-09 is a Thursday (ISO dayOfWeek=4, dowIndex=3): the current week's Monday is
    // 2026-07-06, and the heatmap window starts 13 full weeks before that.
    private val expectedHeatmapStart = LocalDate.of(2026, 7, 6).minusDays(13L * 7)
    private val expectedHeatmapSize = ChronoUnit.DAYS.between(expectedHeatmapStart, LocalDate.of(2026, 7, 9)).toInt() + 1

    @Test
    fun emptyHistoryYieldsAllZeros() {
        val stats = computeMealStats(emptyList(), MealStatsRange.ALL, now, zone)

        assertEquals(0, stats.mealsLogged)
        assertEquals(0, stats.activeDays)
        assertEquals(0, stats.currentStreak)
        assertEquals(0, stats.longestStreak)
        assertEquals(0.0, stats.avgDailyCaloriesKcal, 0.0)
        assertEquals(expectedHeatmapSize, stats.heatmap.size)
        assertEquals(expectedHeatmapStart, stats.heatmap.keys.min())
        assertEquals(LocalDate.of(2026, 7, 9), stats.heatmap.keys.max())
        assertTrue(stats.heatmap.values.all { it == 0.0 })
    }

    @Test
    fun singleDayHistoryCountsOneActiveDayAndStreakOfOne() {
        val meals = listOf(meal(1, LocalDate.of(2026, 7, 9)))

        val stats = computeMealStats(meals, MealStatsRange.ALL, now, zone)

        assertEquals(1, stats.mealsLogged)
        assertEquals(1, stats.activeDays)
        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.longestStreak)
    }

    @Test
    fun gapBreaksCurrentStreakButLongestStreakReflectsEarlierRun() {
        val meals = listOf(
            meal(1, LocalDate.of(2026, 7, 1)),
            meal(2, LocalDate.of(2026, 7, 2)),
            meal(3, LocalDate.of(2026, 7, 3)),
            // gap on 2026-07-04
            meal(4, LocalDate.of(2026, 7, 5)),
            meal(5, LocalDate.of(2026, 7, 6)),
        )

        val stats = computeMealStats(meals, MealStatsRange.ALL, now, zone)

        assertEquals(2, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
    }

    @Test
    fun streakIsComputedOverFullHistoryEvenWhenRangeIsNarrower() {
        val meals = (0 until 45).map { offset ->
            meal(offset.toLong(), LocalDate.of(2026, 7, 9).minusDays(offset.toLong()))
        }

        val stats = computeMealStats(meals, MealStatsRange.LAST_7_DAYS, now, zone)

        assertEquals(45, stats.currentStreak)
        assertEquals(45, stats.longestStreak)
        assertEquals(7, stats.mealsLogged)
    }

    @Test
    fun last7DaysRangeScopesMealsLoggedButNotHeatmap() {
        val meals = (0 until 10).map { offset ->
            meal(offset.toLong(), LocalDate.of(2026, 7, 9).minusDays(offset.toLong()))
        }

        val stats = computeMealStats(meals, MealStatsRange.LAST_7_DAYS, now, zone)

        assertEquals(7, stats.mealsLogged)
        assertEquals(7, stats.activeDays)
        assertEquals(expectedHeatmapSize, stats.heatmap.size)
        assertEquals(expectedHeatmapStart, stats.heatmap.keys.min())
        assertEquals(LocalDate.of(2026, 7, 9), stats.heatmap.keys.max())
    }

    @Test
    fun heatmapWindowIsFixedRegardlessOfSelectedRange() {
        val meals = listOf(meal(1, LocalDate.of(2026, 7, 9).minusDays(89)))

        val allStats = computeMealStats(meals, MealStatsRange.ALL, now, zone)
        val sevenDayStats = computeMealStats(meals, MealStatsRange.LAST_7_DAYS, now, zone)
        val thirtyDayStats = computeMealStats(meals, MealStatsRange.LAST_30_DAYS, now, zone)

        assertEquals(expectedHeatmapSize, allStats.heatmap.size)
        assertEquals(allStats.heatmap, sevenDayStats.heatmap)
        assertEquals(allStats.heatmap, thirtyDayStats.heatmap)
    }

    @Test
    fun avgDailyCaloriesIgnoresUntrackedDaysAndAveragesOverTrackedDaysOnly() {
        val meals = listOf(
            meal(1, LocalDate.of(2026, 7, 9), calories = 500.0),
            meal(2, LocalDate.of(2026, 7, 8), calories = 300.0),
        )

        val stats = computeMealStats(meals, MealStatsRange.LAST_7_DAYS, now, zone)

        assertEquals(800.0 / 2, stats.avgDailyCaloriesKcal, 0.0001)
    }

    @Test
    fun avgDailyCaloriesIsZeroWhenNoDayInRangeHasCalories() {
        val stats = computeMealStats(emptyList(), MealStatsRange.LAST_7_DAYS, now, zone)

        assertEquals(0.0, stats.avgDailyCaloriesKcal, 0.0001)
    }

    @Test
    fun multipleMealsSameDayCountOnceForActiveDaysButAllForMealsLogged() {
        val meals = listOf(
            meal(1, LocalDate.of(2026, 7, 9), hour = 8, calories = 300.0),
            meal(2, LocalDate.of(2026, 7, 9), hour = 12, calories = 500.0),
            meal(3, LocalDate.of(2026, 7, 9), hour = 18, calories = 700.0),
            meal(4, LocalDate.of(2026, 7, 8), hour = 8, calories = 400.0),
        )

        val stats = computeMealStats(meals, MealStatsRange.ALL, now, zone)

        assertEquals(4, stats.mealsLogged)
        assertEquals(2, stats.activeDays)
        assertEquals(1500.0, stats.heatmap[LocalDate.of(2026, 7, 9)]!!, 0.0001)
        assertEquals(400.0, stats.heatmap[LocalDate.of(2026, 7, 8)]!!, 0.0001)
    }

    @Test
    fun futureMealIsIncludedInAllRangeCounts() {
        val meals = listOf(
            meal(1, LocalDate.of(2026, 7, 9), calories = 500.0),
            meal(2, LocalDate.of(2026, 7, 12), calories = 300.0),
        )

        val stats = computeMealStats(meals, MealStatsRange.ALL, now, zone)

        assertEquals(2, stats.mealsLogged)
        assertEquals(2, stats.activeDays)
        assertEquals(400.0, stats.avgDailyCaloriesKcal, 0.0001)
        assertEquals(300.0, stats.heatmap[LocalDate.of(2026, 7, 12)]!!, 0.0001)
    }

    @Test
    fun currentStreakCountsAFutureOnlyLoggedDate() {
        val meals = listOf(meal(1, LocalDate.of(2026, 7, 12)))

        val stats = computeMealStats(meals, MealStatsRange.ALL, now, zone)

        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.longestStreak)
    }

    @Test
    fun currentStreakExtendsThroughTodayIntoAFutureDate() {
        val meals = listOf(
            meal(1, LocalDate.of(2026, 7, 9)),
            meal(2, LocalDate.of(2026, 7, 10)),
            meal(3, LocalDate.of(2026, 7, 11)),
            meal(4, LocalDate.of(2026, 7, 12)),
        )

        val stats = computeMealStats(meals, MealStatsRange.ALL, now, zone)

        assertEquals(4, stats.currentStreak)
        assertEquals(4, stats.longestStreak)
    }

    @Test
    fun last7DaysWindowShiftsForwardWhenLatestMealIsInTheFuture() {
        val meals = listOf(
            meal(1, LocalDate.of(2026, 7, 3)),
            meal(2, LocalDate.of(2026, 7, 6)),
            meal(3, LocalDate.of(2026, 7, 12)),
        )

        val stats = computeMealStats(meals, MealStatsRange.LAST_7_DAYS, now, zone)

        assertEquals(LocalDate.of(2026, 7, 6), stats.rangeStartDate)
        assertEquals(2, stats.mealsLogged)
        assertEquals(2, stats.activeDays)
    }

    @Test
    fun heatmapWindowShiftsRightmostDateWhenLatestMealIsInTheFuture() {
        val meals = listOf(meal(1, LocalDate.of(2026, 7, 12), calories = 250.0))

        val stats = computeMealStats(meals, MealStatsRange.ALL, now, zone)

        val expectedShiftedStart = LocalDate.of(2026, 7, 6).minusDays(13L * 7)
        assertEquals(LocalDate.of(2026, 7, 12), stats.heatmap.keys.max())
        assertEquals(expectedShiftedStart, stats.heatmap.keys.min())
        assertEquals(250.0, stats.heatmap[LocalDate.of(2026, 7, 12)]!!, 0.0001)
    }

    @Test
    fun nutritionScoreIsNullForDayWithNoData() {
        assertEquals(null, nutritionScore(DailyNutritionTotals(0.0, 0.0, 0.0, 0.0)))
    }

    @Test
    fun defaultGoalsDeriveExpectedMacroTargets() {
        val targets = NutritionGoals.DEFAULT.macroTargets()
        assertEquals(180.0, targets.proteinG, 0.0001)
        assertEquals(240.0, targets.carbsG, 0.0001)
        assertEquals(80.0, targets.fatG, 0.0001)
    }

    @Test
    fun macroTargetsScaleWithCalorieGoal() {
        val targets = NutritionGoals(3000, 30, 40, 30).macroTargets()
        assertEquals(225.0, targets.proteinG, 0.0001)
        assertEquals(300.0, targets.carbsG, 0.0001)
        assertEquals(100.0, targets.fatG, 0.0001)
    }

    @Test
    fun calorieAdherencePenalizesOvershootMoreThanUndershoot() {
        assertEquals(100.0, calorieAdherence(2400.0, 2400.0), 0.0001) // on target
        assertEquals(100.0, calorieAdherence(2520.0, 2400.0), 0.0001) // r=1.05 band edge
        assertEquals(75.0, calorieAdherence(2640.0, 2400.0), 0.0001) // r=1.10 over
        assertEquals(83.3333, calorieAdherence(2160.0, 2400.0), 0.001) // r=0.90 under
        assertEquals(0.0, calorieAdherence(3000.0, 2400.0), 0.0001) // r=1.25 over bound
        assertEquals(0.0, calorieAdherence(1560.0, 2400.0), 0.0001) // r=0.65 under bound
    }

    @Test
    fun proteinAdherenceHasFirmFloorAndSoftCeiling() {
        assertEquals(100.0, proteinAdherence(180.0, 180.0), 0.0001)
        assertEquals(100.0, proteinAdherence(270.0, 180.0), 0.0001) // 1.5x not penalized
        assertEquals(50.0, proteinAdherence(135.0, 180.0), 0.0001) // r=0.75
        assertEquals(0.0, proteinAdherence(90.0, 180.0), 0.0001) // r=0.5 floor
    }

    @Test
    fun carbsAndFatScoreZeroWhenAbsentAgainstNonZeroTargets() {
        assertEquals(0.0, carbsAdherence(0.0, 240.0), 0.0001)
        assertEquals(0.0, fatAdherence(0.0, 80.0), 0.0001)
        assertEquals(100.0, carbsAdherence(240.0, 240.0), 0.0001)
        assertEquals(100.0, fatAdherence(80.0, 80.0), 0.0001)
    }

    @Test
    fun onTargetDayScores100() {
        val totals = DailyNutritionTotals(caloriesKcal = 2400.0, proteinG = 180.0, carbsG = 240.0, fatG = 80.0)
        assertEquals(100.0, nutritionScore(totals, NutritionGoals.DEFAULT)!!, 0.0001)
    }

    @Test
    fun zeroCarbsAndFatNoLongerScore100() {
        val totals = DailyNutritionTotals(caloriesKcal = 2400.0, proteinG = 180.0, carbsG = 0.0, fatG = 0.0)
        // calories 100, protein 100, carbs 0, fat 0 -> base = 100*0.40 + 100*0.30 = 70
        assertEquals(70.0, nutritionScore(totals, NutritionGoals.DEFAULT)!!, 0.0001)
    }

    @Test
    fun activityRaisesTheCalorieTarget() {
        val totals = DailyNutritionTotals(caloriesKcal = 2700.0, proteinG = 180.0, carbsG = 240.0, fatG = 80.0)
        // goal 2400 + 0.5*600 = 2700 target -> calories on target -> full score
        assertEquals(100.0, nutritionScore(totals, NutritionGoals.DEFAULT, activityBurnedKcal = 600.0)!!, 0.0001)
        // without the activity add-back, 2700 kcal overshoots the 2400 target and scores lower
        assertTrue(nutritionScore(totals, NutritionGoals.DEFAULT, activityBurnedKcal = 0.0)!! < 100.0)
    }

    @Test
    fun qualityPenaltyIsCappedAndProportional() {
        val target = 2400.0
        val within = DailyNutritionTotals(caloriesKcal = 2400.0, proteinG = 0.0, carbsG = 0.0, fatG = 0.0)
        assertEquals(1.0, qualityMultiplier(within, target), 0.0001)
        // sugar 120g against a 60g limit (2x) docks 0.10
        val highSugar = within.copy(sugarG = 120.0)
        assertEquals(0.90, qualityMultiplier(highSugar, target), 0.0001)
        // all three at >=2x their limits floor the multiplier at 0.70
        val allOver = within.copy(saturatedFatG = 60.0, sugarG = 120.0, saltG = 10.0)
        assertEquals(0.70, qualityMultiplier(allOver, target), 0.0001)
    }

    private fun meal(id: Long, date: LocalDate, hour: Int = 9, calories: Double = 1.0): SavedMeal = SavedMeal(
        id = id,
        createdAtEpochMillis = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        title = "meal $id",
        query = "meal $id",
        items = emptyList(),
        totals = NutritionTotals(
            caloriesKcal = calories,
            amountGml = 1.0,
            proteinG = 1.0,
            carbsG = 1.0,
            fatG = 1.0,
            saturatedFatG = 0.0,
            sugarG = 0.0,
            saltG = 0.0,
        ),
        assumptions = emptyList(),
        warnings = emptyList(),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = false,
    )
}
