package com.example.vocalorie.ui.entries

import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.model.SavedMeal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

const val MIN_ROLLING_STATS_MINUTES: Int = 15
const val MAX_ROLLING_STATS_MINUTES: Int = 24 * 60
const val ROLLING_STATS_STEP_MINUTES: Int = 15
const val DEFAULT_ROLLING_STATS_MINUTES: Int = 4 * 60
const val CALORIES_HISTOGRAM_BUCKET_MINUTES: Int = 15

data class MealTimeWindow(
    val label: String,
    val startInclusive: Instant,
    val end: Instant,
    val endInclusive: Boolean,
) {
    fun contains(epochMillis: Long): Boolean {
        val instant = Instant.ofEpochMilli(epochMillis)
        val afterStart = !instant.isBefore(startInclusive)
        val beforeEnd = if (endInclusive) !instant.isAfter(end) else instant.isBefore(end)
        return afterStart && beforeEnd
    }
}

data class MealNutritionStats(
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val amountGml: Double,
)

enum class MealStatsWindowMode {
    SINCE_MIDNIGHT,
    LAST_24_HOURS,
    CUSTOM,
}

data class MealStatsWindowSelection(
    val mode: MealStatsWindowMode = MealStatsWindowMode.SINCE_MIDNIGHT,
    val customDuration: Duration = Duration.ofMinutes(DEFAULT_ROLLING_STATS_MINUTES.toLong()),
)

data class LabeledMealStats(
    val label: String,
    val stats: MealNutritionStats,
)

data class MealCaloriesBucket(
    val startInclusive: Instant,
    val end: Instant,
    val caloriesKcal: Double,
)

fun selectedDayWindow(dayOffset: Int, now: Instant, zone: ZoneId): MealTimeWindow {
    require(dayOffset >= 0) { "dayOffset must be non-negative" }
    val date = LocalDate.ofInstant(now, zone).minusDays(dayOffset.toLong())

    if (dayOffset == 0) {
        return MealTimeWindow(
            label = dateLabel(dayOffset, date),
            startInclusive = date.atStartOfDay(zone).toInstant(),
            end = now,
            endInclusive = true,
        )
    }

    return MealTimeWindow(
        label = dateLabel(dayOffset, date),
        startInclusive = date.atStartOfDay(zone).toInstant(),
        end = date.plusDays(1).atStartOfDay(zone).toInstant(),
        endInclusive = false,
    )
}

fun filterMealsForDay(meals: List<SavedMeal>, dayOffset: Int, now: Instant, zone: ZoneId): List<SavedMeal> {
    val window = selectedDayWindow(dayOffset, now, zone)
    return meals
        .filter { window.contains(it.createdAtEpochMillis) }
        .sortedByDescending { it.createdAtEpochMillis }
}

fun selectedTimelineStats(
    meals: List<SavedMeal>,
    now: Instant,
    zone: ZoneId,
    selectedDayOffset: Int = 0,
    selection: MealStatsWindowSelection = MealStatsWindowSelection(),
): LabeledMealStats {
    val window = selectedStatsWindow(selection, now, zone, selectedDayOffset)
    return LabeledMealStats(
        label = window.label,
        stats = meals.filter { window.contains(it.createdAtEpochMillis) }.sumNutrition(),
    )
}

fun selectedCaloriesHistogram(
    meals: List<SavedMeal>,
    now: Instant,
    zone: ZoneId,
    selectedDayOffset: Int = 0,
    selection: MealStatsWindowSelection = MealStatsWindowSelection(),
): List<MealCaloriesBucket> {
    val window = selectedStatsWindow(selection, now, zone, selectedDayOffset)
    return selectedCaloriesHistogramForWindow(meals, window)
}

fun selectedDayCaloriesHistogram(meals: List<SavedMeal>, now: Instant, zone: ZoneId, selectedDayOffset: Int = 0): List<MealCaloriesBucket> {
    val window = selectedDayHistogramWindow(selectedDayOffset, now, zone)
    return selectedCaloriesHistogramForWindow(meals, window)
}

private fun selectedCaloriesHistogramForWindow(meals: List<SavedMeal>, window: MealTimeWindow): List<MealCaloriesBucket> {
    val bucketDuration = Duration.ofMinutes(CALORIES_HISTOGRAM_BUCKET_MINUTES.toLong())
    val bucketMillis = bucketDuration.toMillis()
    val windowMillis = Duration.between(window.startInclusive, window.end).toMillis().coerceAtLeast(0L)
    val bucketCount = ((windowMillis + bucketMillis - 1) / bucketMillis).toInt().coerceAtLeast(1)
    val caloriesByBucket = DoubleArray(bucketCount)

    meals
        .filter { window.contains(it.createdAtEpochMillis) }
        .forEach { meal ->
            val mealInstant = Instant.ofEpochMilli(meal.createdAtEpochMillis)
            val millisFromStart = Duration.between(window.startInclusive, mealInstant).toMillis().coerceAtLeast(0L)
            val bucketIndex = (millisFromStart / bucketMillis).toInt().coerceIn(0, bucketCount - 1)
            caloriesByBucket[bucketIndex] += meal.totals.caloriesKcal ?: 0.0
        }

    return List(bucketCount) { index ->
        val start = window.startInclusive.plus(bucketDuration.multipliedBy(index.toLong()))
        MealCaloriesBucket(
            startInclusive = start,
            end = start.plus(bucketDuration).coerceAtMost(window.end),
            caloriesKcal = caloriesByBucket[index],
        )
    }
}

private fun selectedDayHistogramWindow(dayOffset: Int, now: Instant, zone: ZoneId): MealTimeWindow {
    require(dayOffset >= 0) { "dayOffset must be non-negative" }
    val date = LocalDate.ofInstant(now, zone).minusDays(dayOffset.toLong())
    return MealTimeWindow(
        label = dateLabel(dayOffset, date),
        startInclusive = date.atStartOfDay(zone).toInstant(),
        end = date.plusDays(1).atStartOfDay(zone).toInstant(),
        endInclusive = false,
    )
}

fun selectedStatsWindow(selection: MealStatsWindowSelection, now: Instant, zone: ZoneId, selectedDayOffset: Int = 0): MealTimeWindow {
    val startOfToday = LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant()

    return when (selection.mode) {
        MealStatsWindowMode.SINCE_MIDNIGHT -> {
            if (selectedDayOffset == 0) {
                MealTimeWindow("Since 00:00", startOfToday, now, endInclusive = true)
            } else {
                val selectedDay = selectedDayWindow(selectedDayOffset, now, zone)
                MealTimeWindow("Since 00:00", selectedDay.startInclusive, selectedDay.end, endInclusive = selectedDay.endInclusive)
            }
        }
        MealStatsWindowMode.LAST_24_HOURS -> MealTimeWindow("Last 24h", now.minus(Duration.ofHours(24)), now, endInclusive = true)
        MealStatsWindowMode.CUSTOM -> {
            val rollingDuration = normalizeRollingStatsDuration(selection.customDuration)
            MealTimeWindow(formatRollingStatsLabel(rollingDuration), now.minus(rollingDuration), now, endInclusive = true)
        }
    }
}

fun normalizeRollingStatsDuration(duration: Duration): Duration {
    val roundedMinutes = (duration.toMinutes().toDouble() / ROLLING_STATS_STEP_MINUTES).roundToInt() * ROLLING_STATS_STEP_MINUTES
    return Duration.ofMinutes(roundedMinutes.coerceIn(MIN_ROLLING_STATS_MINUTES, MAX_ROLLING_STATS_MINUTES).toLong())
}

fun formatRollingStatsLabel(duration: Duration): String {
    val minutes = normalizeRollingStatsDuration(duration).toMinutes().toInt()
    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    val durationLabel = when {
        hours == 0 -> "$remainingMinutes mins"
        remainingMinutes == 0 -> "${hours}h"
        else -> "${hours}h $remainingMinutes mins"
    }
    return "Last $durationLabel"
}

private fun List<SavedMeal>.sumNutrition(): MealNutritionStats = fold(MealNutritionStats(0.0, 0.0, 0.0, 0.0, 0.0)) { acc, meal ->
    val totals: NutritionTotals = meal.totals
    acc.copy(
        caloriesKcal = acc.caloriesKcal + (totals.caloriesKcal ?: 0.0),
        proteinG = acc.proteinG + (totals.proteinG ?: 0.0),
        carbsG = acc.carbsG + (totals.carbsG ?: 0.0),
        fatG = acc.fatG + (totals.fatG ?: 0.0),
        amountGml = acc.amountGml + (totals.amountGml ?: 0.0),
    )
}

private fun dateLabel(dayOffset: Int, date: LocalDate): String = when (dayOffset) {
    0 -> "Today · ${DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date)}"
    1 -> "Yesterday · ${DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date)}"
    else -> DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date)
}
