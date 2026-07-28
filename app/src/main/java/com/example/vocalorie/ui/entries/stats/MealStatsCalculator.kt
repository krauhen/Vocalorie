package com.example.vocalorie.ui.entries.stats

import com.example.vocalorie.model.NutritionGoals
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
                saturatedFatG = acc.saturatedFatG + (meal.totals.saturatedFatG ?: 0.0),
                sugarG = acc.sugarG + (meal.totals.sugarG ?: 0.0),
                saltG = acc.saltG + (meal.totals.saltG ?: 0.0),
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
 * Goal-adherence 0-100 nutrition score for a day, or null if no meals were logged that day.
 *
 * `score = base × qualityMultiplier`, where `base` is a weighted average of four asymmetric
 * adherence sub-scores against the user's [goals] (calories weight 0.40, protein 0.30, carbs 0.15,
 * fat 0.15) and `qualityMultiplier` is the capped saturated-fat/sugar/salt penalty. The calorie
 * target is raised by 50% of [activityBurnedKcal] (activity expands the day's allowance).
 */
fun nutritionScore(
    totals: DailyNutritionTotals,
    goals: NutritionGoals = NutritionGoals.DEFAULT,
    activityBurnedKcal: Double = 0.0,
): Double? {
    if (!totals.hasData()) return null
    val targets = goals.macroTargets()
    val calorieTarget = goals.calorieGoalKcal + 0.5 * activityBurnedKcal
    val caloriesScore = calorieAdherence(totals.caloriesKcal, calorieTarget)
    val proteinScore = proteinAdherence(totals.proteinG, targets.proteinG)
    val carbsScore = carbsAdherence(totals.carbsG, targets.carbsG)
    val fatScore = fatAdherence(totals.fatG, targets.fatG)
    val base = caloriesScore * 0.40 + proteinScore * 0.30 + carbsScore * 0.15 + fatScore * 0.15
    return (base * qualityMultiplier(totals, calorieTarget)).coerceIn(0.0, 100.0)
}

/** Linear falloff from 100 at [full] to 0 at [zero] for a ratio, clamped 0-100. Works either direction. */
private fun falloff(ratio: Double, full: Double, zero: Double): Double =
    ((ratio - zero) / (full - zero) * 100.0).coerceIn(0.0, 100.0)

/**
 * Calories — asymmetric U about the (activity-adjusted) target: full credit for r in [0.95, 1.05],
 * steeper falloff to 0 at r = 1.25 over, gentler falloff to 0 at r = 0.65 under.
 */
internal fun calorieAdherence(calories: Double, target: Double): Double {
    if (target <= 0.0) return if (calories <= 0.0) 100.0 else 0.0
    val r = calories / target
    return when {
        r in 0.95..1.05 -> 100.0
        r > 1.05 -> falloff(r, full = 1.05, zero = 1.25)
        else -> falloff(r, full = 0.95, zero = 0.65)
    }
}

/** Protein — firm floor, soft ceiling: 100 at r >= 1.0, steep falloff to 0 at r = 0.5. */
internal fun proteinAdherence(protein: Double, target: Double): Double {
    if (target <= 0.0) return 100.0
    val r = protein / target
    return if (r >= 1.0) 100.0 else falloff(r, full = 1.0, zero = 0.5)
}

/** Carbs — flexible: full credit for r in [0.8, 1.2], falloff to 0 at r = 0.4 under / r = 1.8 over. */
internal fun carbsAdherence(carbs: Double, target: Double): Double {
    if (target <= 0.0) return if (carbs <= 0.0) 100.0 else 0.0
    val r = carbs / target
    return when {
        r in 0.8..1.2 -> 100.0
        r > 1.2 -> falloff(r, full = 1.2, zero = 1.8)
        else -> falloff(r, full = 0.8, zero = 0.4)
    }
}

/** Fat — full credit for r in [0.8, 1.2], falloff to 0 at r = 0.4 under / r = 1.6 over (steeper). */
internal fun fatAdherence(fat: Double, target: Double): Double {
    if (target <= 0.0) return if (fat <= 0.0) 100.0 else 0.0
    val r = fat / target
    return when {
        r in 0.8..1.2 -> 100.0
        r > 1.2 -> falloff(r, full = 1.2, zero = 1.6)
        else -> falloff(r, full = 0.8, zero = 0.4)
    }
}

/**
 * Capped saturated-fat/sugar/salt quality penalty, in [0.70, 1.0]. Limits: saturated fat 10% of
 * the calorie target's energy, sugar 10%, salt a fixed 5 g/day. Each nutrient's overage saturates
 * at twice its limit and docks up to 0.10; total dock capped at 0.30.
 */
internal fun qualityMultiplier(totals: DailyNutritionTotals, calorieTarget: Double): Double {
    val satFatLimit = 0.10 * calorieTarget / 9.0
    val sugarLimit = 0.10 * calorieTarget / 4.0
    val saltLimit = 5.0
    val penalty = 0.10 * overage(totals.saturatedFatG, satFatLimit) +
        0.10 * overage(totals.sugarG, sugarLimit) +
        0.10 * overage(totals.saltG, saltLimit)
    return 1.0 - penalty
}

/** Fraction over [limit], saturating at twice the limit: 0 when at/under, 1 at 2x or beyond. */
private fun overage(value: Double, limit: Double): Double =
    if (limit <= 0.0) 0.0 else ((value - limit) / limit).coerceIn(0.0, 1.0)

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
