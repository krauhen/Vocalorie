package com.example.vocalorie.ui.entries

import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.model.SavedMeal

data class MealBundle(
    val id: String,
    val meals: List<SavedMeal>,
    val totals: NutritionTotals,
    val startTimeEpochMillis: Long,
    val endTimeEpochMillis: Long,
) {
    val isMultiEntry: Boolean get() = meals.size > 1
}

fun bundleMealsByTime(
    meals: List<SavedMeal>,
    gapThresholdMillis: Long = 15 * 60 * 1000L,
): List<MealBundle> {
    if (meals.isEmpty()) return emptyList()

    val sorted = meals.sortedBy { it.createdAtEpochMillis }
    val bundles = mutableListOf<MealBundle>()
    var current = mutableListOf(sorted.first())

    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1]
        val next = sorted[i]
        if (next.createdAtEpochMillis - prev.createdAtEpochMillis <= gapThresholdMillis) {
            current.add(next)
        } else {
            bundles.add(current.toMealBundle())
            current = mutableListOf(next)
        }
    }
    bundles.add(current.toMealBundle())
    return bundles
}

private fun MutableList<SavedMeal>.toMealBundle(): MealBundle {
    val first = first()
    val last = last()
    return MealBundle(
        id = "bundle_${first.id}_${last.id}",
        meals = toList(),
        totals = map { it.totals }.sumNutritionTotals(),
        startTimeEpochMillis = first.createdAtEpochMillis,
        endTimeEpochMillis = last.createdAtEpochMillis,
    )
}

private fun List<NutritionTotals>.sumNutritionTotals(): NutritionTotals = fold(
    NutritionTotals(
        caloriesKcal = 0.0,
        amountGml = 0.0,
        proteinG = 0.0,
        carbsG = 0.0,
        fatG = 0.0,
        saturatedFatG = 0.0,
        sugarG = 0.0,
        saltG = 0.0,
    ),
) { acc, totals ->
    val cals = totals.caloriesKcal ?: 0.0
    val amt = totals.amountGml ?: 0.0
    val prot = totals.proteinG ?: 0.0
    val carb = totals.carbsG ?: 0.0
    val fat = totals.fatG ?: 0.0
    val satFat = totals.saturatedFatG ?: 0.0
    val sugar = totals.sugarG ?: 0.0
    val salt = totals.saltG ?: 0.0
    acc.copy(
        caloriesKcal = (acc.caloriesKcal ?: 0.0) + cals,
        amountGml = (acc.amountGml ?: 0.0) + amt,
        proteinG = (acc.proteinG ?: 0.0) + prot,
        carbsG = (acc.carbsG ?: 0.0) + carb,
        fatG = (acc.fatG ?: 0.0) + fat,
        saturatedFatG = (acc.saturatedFatG ?: 0.0) + satFat,
        sugarG = (acc.sugarG ?: 0.0) + sugar,
        saltG = (acc.saltG ?: 0.0) + salt,
    )
}
