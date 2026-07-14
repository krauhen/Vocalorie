package com.example.vocalorie.ui.entries.stats

import com.example.vocalorie.model.SavedMeal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val HEATMAP_COLUMN_COUNT: Int = 14
const val HEATMAP_ROW_COUNT: Int = 7

fun computeMealStats(
    meals: List<SavedMeal>,
    range: MealStatsRange,
    now: Instant,
    zone: ZoneId,
): MealStats {
    val today = LocalDate.ofInstant(now, zone)
    val datedMeals = meals.map { it to it.localDate(zone) }
    val latestDate = datedMeals.maxOfOrNull { it.second } ?: today
    val effectiveEnd = if (latestDate.isAfter(today)) latestDate else today

    val rangeStartDate = when (range) {
        MealStatsRange.ALL -> datedMeals.minOfOrNull { it.second } ?: today
        MealStatsRange.LAST_30_DAYS -> effectiveEnd.minusDays(29)
        MealStatsRange.LAST_7_DAYS -> effectiveEnd.minusDays(6)
    }

    val mealsInRange = datedMeals.filter { (_, date) -> !date.isBefore(rangeStartDate) && !date.isAfter(effectiveEnd) }
    val heatmapStartDate = heatmapStartDate(effectiveEnd)
    val heatmap = buildHeatmap(datedMeals, heatmapStartDate, effectiveEnd)
    val dailyTotals = buildDailyTotals(datedMeals, heatmapStartDate, effectiveEnd)

    val allLoggedDates = datedMeals.map { it.second }.toSortedSet()
    val longestStreak = longestStreak(allLoggedDates)
    val currentStreak = currentStreak(allLoggedDates)

    return MealStats(
        mealsLogged = mealsInRange.size,
        activeDays = mealsInRange.map { it.second }.distinct().size,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        avgDailyCaloriesKcal = avgDailyCalories(mealsInRange),
        heatmap = heatmap,
        rangeStartDate = rangeStartDate,
        dailyTotals = dailyTotals,
    )
}

private fun avgDailyCalories(mealsInRange: List<Pair<SavedMeal, LocalDate>>): Double {
    val trackedDailyCalories = mealsInRange
        .groupingBy { it.second }
        .fold(0.0) { acc, (meal, _) -> acc + (meal.totals.caloriesKcal ?: 0.0) }
        .values
        .filter { it > 0.0 }
    if (trackedDailyCalories.isEmpty()) return 0.0
    return trackedDailyCalories.sum() / trackedDailyCalories.size
}

private fun SavedMeal.localDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(createdAtEpochMillis).atZone(zone).toLocalDate()

/**
 * Aligns the heatmap window to whole Mon-Sun calendar weeks: the leftmost of
 * [HEATMAP_COLUMN_COUNT] weeks is always complete, and the rightmost is the current
 * (possibly partial) week ending at [end].
 */
private fun heatmapStartDate(end: LocalDate): LocalDate {
    val dowIndex = end.dayOfWeek.value - 1
    val currentWeekMonday = end.minusDays(dowIndex.toLong())
    return currentWeekMonday.minusDays((HEATMAP_COLUMN_COUNT - 1).toLong() * 7)
}

private fun buildHeatmap(datedMeals: List<Pair<SavedMeal, LocalDate>>, startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Double> {
    val caloriesByDate = datedMeals
        .filter { (_, date) -> !date.isBefore(startDate) && !date.isAfter(endDate) }
        .groupingBy { it.second }
        .fold(0.0) { acc, (meal, _) -> acc + (meal.totals.caloriesKcal ?: 0.0) }
    if (startDate.isAfter(endDate)) return emptyMap()
    val days = generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }
    return days.associateWith { caloriesByDate[it] ?: 0.0 }
}

private fun buildDailyTotals(datedMeals: List<Pair<SavedMeal, LocalDate>>, startDate: LocalDate, endDate: LocalDate): Map<LocalDate, DailyNutritionTotals> {
    val dailyAggregates = datedMeals
        .filter { (_, date) -> !date.isBefore(startDate) && !date.isAfter(endDate) }
        .groupingBy { it.second }
        .fold(DailyNutritionTotals(0.0, 0.0, 0.0, 0.0)) { acc, (meal, _) ->
            acc.copy(
                caloriesKcal = acc.caloriesKcal + (meal.totals.caloriesKcal ?: 0.0),
                proteinG = acc.proteinG + (meal.totals.proteinG ?: 0.0),
                carbsG = acc.carbsG + (meal.totals.carbsG ?: 0.0),
                fatG = acc.fatG + (meal.totals.fatG ?: 0.0),
            )
        }
    if (startDate.isAfter(endDate)) return emptyMap()
    val days = generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }
    return days.associateWith { dailyAggregates[it] ?: DailyNutritionTotals(0.0, 0.0, 0.0, 0.0) }
}

private fun longestStreak(sortedDates: Set<LocalDate>): Int {
    var longest = 0
    var current = 0
    var previous: LocalDate? = null
    for (date in sortedDates) {
        current = if (previous != null && previous.plusDays(1) == date) current + 1 else 1
        longest = maxOf(longest, current)
        previous = date
    }
    return longest
}

/**
 * Whether a day has any logged nutrition at all. Days with all-zero totals are treated as
 * "no data" rather than a worst-case score.
 */
fun DailyNutritionTotals.hasData(): Boolean =
    caloriesKcal > 0.0 || proteinG > 0.0 || carbsG > 0.0 || fatG > 0.0

/**
 * Weighted 0-100 nutrition score for a day, or null if no meals were logged that day.
 */
fun nutritionScore(totals: DailyNutritionTotals): Double? {
    if (!totals.hasData()) return null
    val caloriesScore = normalizeCalories(totals.caloriesKcal)
    val proteinScore = normalizeProtein(totals.proteinG)
    val carbsScore = normalizeCarbs(totals.carbsG)
    val fatScore = normalizeFat(totals.fatG)
    return (caloriesScore * 10.0 + proteinScore * 3.0 + carbsScore * 2.0 + fatScore * 1.0) / 16.0
}

/** Triangular peak of 100 at 2600 kcal, linear falloff to 0 at 2200 and 3000, clamped beyond. */
internal fun normalizeCalories(calories: Double): Double = when {
    calories <= 2200.0 -> 0.0
    calories <= 2600.0 -> (calories - 2200.0) / (2600.0 - 2200.0) * 100.0
    calories <= 3000.0 -> 100.0 - (calories - 2600.0) / (3000.0 - 2600.0) * 100.0
    else -> 0.0
}

/** 0 at <=90g, linear rise to 100 at 180g, plateaus at 100 above. */
internal fun normalizeProtein(protein: Double): Double = when {
    protein <= 90.0 -> 0.0
    protein >= 180.0 -> 100.0
    else -> (protein - 90.0) / (180.0 - 90.0) * 100.0
}

/** Peak 100 at 0g, linear decay through 90g/180g reference points to 0 at 270g, clamped beyond. */
internal fun normalizeCarbs(carbs: Double): Double = when {
    carbs <= 0.0 -> 100.0
    carbs <= 270.0 -> 100.0 - carbs / 270.0 * 100.0
    else -> 0.0
}

/** Peak 100 at 0g, linear decay to 0 at 90g, clamped beyond. */
internal fun normalizeFat(fat: Double): Double = when {
    fat <= 0.0 -> 100.0
    fat <= 90.0 -> 100.0 - fat / 90.0 * 100.0
    else -> 0.0
}

private fun currentStreak(sortedDates: Set<LocalDate>): Int {
    val mostRecentLoggedDate = sortedDates.lastOrNull() ?: return 0
    var streak = 1
    var cursor = mostRecentLoggedDate
    while (sortedDates.contains(cursor.minusDays(1))) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}
