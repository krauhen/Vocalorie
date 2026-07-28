package com.example.vocalorie.ui.entries.stats

import java.time.LocalDate

enum class MealStatsRange {
    ALL,
    LAST_30_DAYS,
    LAST_7_DAYS,
}

/**
 * Daily nutrition totals for a single day.
 *
 * Saturated fat / sugar / salt default to 0 so existing four-arg call sites keep compiling; they
 * feed the day nutrition score's quality penalty.
 */
data class DailyNutritionTotals(
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val saturatedFatG: Double = 0.0,
    val sugarG: Double = 0.0,
    val saltG: Double = 0.0,
)

data class MealStats(
    val mealsLogged: Int,
    val activeDays: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val avgDailyCaloriesKcal: Double,
    val heatmap: Map<LocalDate, Double>,
    val rangeStartDate: LocalDate,
    val dailyTotals: Map<LocalDate, DailyNutritionTotals>,
)
