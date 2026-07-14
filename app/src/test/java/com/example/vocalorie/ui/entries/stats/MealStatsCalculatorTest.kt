package com.example.vocalorie.ui.entries.stats

import com.example.vocalorie.model.ConfidenceLevel
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
    fun caloriesNormalizationPeaksAt2600AndFallsOffLinearlyClampedAtBounds() {
        assertEquals(0.0, normalizeCalories(2200.0), 0.0001)
        assertEquals(100.0, normalizeCalories(2600.0), 0.0001)
        assertEquals(0.0, normalizeCalories(3000.0), 0.0001)
        assertEquals(0.0, normalizeCalories(2100.0), 0.0001)
        assertEquals(0.0, normalizeCalories(3100.0), 0.0001)
        assertEquals(50.0, normalizeCalories(2400.0), 0.0001)
    }

    @Test
    fun proteinNormalizationRisesFrom90gTo180gThenPlateaus() {
        assertEquals(0.0, normalizeProtein(90.0), 0.0001)
        assertEquals(0.0, normalizeProtein(50.0), 0.0001)
        assertEquals(100.0, normalizeProtein(180.0), 0.0001)
        assertEquals(100.0, normalizeProtein(220.0), 0.0001)
        assertEquals(50.0, normalizeProtein(135.0), 0.0001)
    }

    @Test
    fun carbsNormalizationDecaysFrom0To270Clamped() {
        assertEquals(100.0, normalizeCarbs(0.0), 0.0001)
        assertEquals(0.0, normalizeCarbs(270.0), 0.0001)
        assertEquals(0.0, normalizeCarbs(320.0), 0.0001)
        assertTrue(normalizeCarbs(90.0) < 100.0 && normalizeCarbs(90.0) > 0.0)
    }

    @Test
    fun fatNormalizationDecaysFrom0To90Clamped() {
        assertEquals(100.0, normalizeFat(0.0), 0.0001)
        assertEquals(0.0, normalizeFat(90.0), 0.0001)
        assertEquals(0.0, normalizeFat(150.0), 0.0001)
        assertEquals(50.0, normalizeFat(45.0), 0.0001)
    }

    @Test
    fun weightedScoreCombinesAllFourNormalizedMetrics() {
        // calories=2600 -> 100, protein=180 -> 100, carbs=270 -> 0, fat=90 -> 0
        val totals = DailyNutritionTotals(caloriesKcal = 2600.0, proteinG = 180.0, carbsG = 270.0, fatG = 90.0)

        val score = nutritionScore(totals)!!

        assertEquals((100.0 * 10 + 100.0 * 3 + 0.0 * 2 + 0.0 * 1) / 16.0, score, 0.0001)
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
