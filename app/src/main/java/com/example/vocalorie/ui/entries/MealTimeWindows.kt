package com.example.vocalorie.ui.entries

import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.model.SavedActivity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val saturatedFatG: Double,
    val sugarG: Double,
    val saltG: Double,
)

enum class MealStatsWindowMode {
    SINCE_MIDNIGHT,
    LAST_24_HOURS,
    CUSTOM,
    ;

    /** Single source for the window labels, which were previously spelled out at two call sites. */
    val label: String
        get() = when (this) {
            SINCE_MIDNIGHT -> "Since 00:00"
            LAST_24_HOURS -> "Last 24h"
            CUSTOM -> "Custom"
        }
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

/**
 * Re-anchors a viewed-day [offset] after the calendar day has advanced by [daysPassed]. Offset 0
 * (today) always stays 0 — it tracks "today" by definition. Any other offset shifts forward by the
 * same number of days, so the calendar date it pointed at keeps being shown.
 */
fun dayOffsetAfterDayChange(offset: Int, daysPassed: Long): Int {
    if (offset == 0) return 0
    val shifted = offset.toLong() + daysPassed
    return shifted.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/** Duration from [now] to the next local midnight in [zone], honoring DST transitions in between. */
fun durationUntilNextLocalMidnight(now: Instant, zone: ZoneId): Duration {
    val nextMidnight = LocalDate.ofInstant(now, zone).plusDays(1).atStartOfDay(zone).toInstant()
    return Duration.between(now, nextMidnight)
}

fun selectedDayWindow(dayOffset: Int, now: Instant, zone: ZoneId): MealTimeWindow {
    val date = LocalDate.ofInstant(now, zone).minusDays(dayOffset.toLong())

    return MealTimeWindow(
        label = dateLabel(dayOffset, date),
        startInclusive = date.atStartOfDay(zone).toInstant(),
        end = date.plusDays(1).atStartOfDay(zone).toInstant(),
        endInclusive = false,
    )
}

/**
 * Timestamp for a newly created entry added while viewing the day at [dayOffset]: that calendar
 * day at the current wall-clock time. Offset 0 (today) resolves to ~[now], preserving the prior
 * "added now" behavior; a past/future offset lands the entry on that day at the current time.
 */
fun selectedDayTimestampMillis(dayOffset: Int, now: Instant, zone: ZoneId): Long {
    val date = LocalDate.ofInstant(now, zone).minusDays(dayOffset.toLong())
    val time = LocalTime.ofInstant(now, zone)
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

fun filterMealsForDay(meals: List<SavedMeal>, dayOffset: Int, now: Instant, zone: ZoneId): List<SavedMeal> {
    return filterEntriesForDay(meals, dayOffset, now, zone) { it.createdAtEpochMillis }
}

fun filterActivitiesForDay(activities: List<SavedActivity>, dayOffset: Int, now: Instant, zone: ZoneId): List<SavedActivity> {
    return filterEntriesForDay(activities, dayOffset, now, zone) { it.createdAtEpochMillis }
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
    val date = LocalDate.ofInstant(now, zone).minusDays(dayOffset.toLong())
    return MealTimeWindow(
        label = dateLabel(dayOffset, date),
        startInclusive = date.atStartOfDay(zone).toInstant(),
        end = date.plusDays(1).atStartOfDay(zone).toInstant(),
        endInclusive = false,
    )
}

private inline fun <T> filterEntriesForDay(
    entries: List<T>,
    dayOffset: Int,
    now: Instant,
    zone: ZoneId,
    crossinline createdAtEpochMillis: (T) -> Long,
): List<T> {
    val window = selectedDayWindow(dayOffset, now, zone)
    return entries
        .filter { window.contains(createdAtEpochMillis(it)) }
        .sortedByDescending { createdAtEpochMillis(it) }
}

fun selectedStatsWindow(selection: MealStatsWindowSelection, now: Instant, zone: ZoneId, selectedDayOffset: Int = 0): MealTimeWindow {
    return when (selection.mode) {
        MealStatsWindowMode.SINCE_MIDNIGHT -> {
            val selectedDay = selectedDayWindow(selectedDayOffset, now, zone)
            MealTimeWindow("Since 00:00", selectedDay.startInclusive, selectedDay.end, endInclusive = selectedDay.endInclusive)
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

fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0 -> "${remainingMinutes}m"
        remainingMinutes == 0 -> "${hours}h"
        else -> "${hours}h ${remainingMinutes}m"
    }
}

fun dailyEnergyBalance(consumedKcal: Double, baseBurnKcal: Double, activitiesBurnedKcal: Double): Double =
    consumedKcal - baseBurnKcal - activitiesBurnedKcal

private fun List<SavedMeal>.sumNutrition(): MealNutritionStats = fold(MealNutritionStats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)) { acc, meal ->
    val totals: NutritionTotals = meal.totals
    acc.copy(
        caloriesKcal = acc.caloriesKcal + (totals.caloriesKcal ?: 0.0),
        proteinG = acc.proteinG + (totals.proteinG ?: 0.0),
        carbsG = acc.carbsG + (totals.carbsG ?: 0.0),
        fatG = acc.fatG + (totals.fatG ?: 0.0),
        amountGml = acc.amountGml + (totals.amountGml ?: 0.0),
        saturatedFatG = acc.saturatedFatG + (totals.saturatedFatG ?: 0.0),
        sugarG = acc.sugarG + (totals.sugarG ?: 0.0),
        saltG = acc.saltG + (totals.saltG ?: 0.0),
    )
}

private fun dateLabel(dayOffset: Int, date: LocalDate): String {
    val formatted = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()).format(date)
    return when (dayOffset) {
        0 -> "Today · $formatted"
        1 -> "Yesterday · $formatted"
        else -> formatted
    }
}
