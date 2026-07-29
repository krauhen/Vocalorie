package com.example.vocalorie.data

import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.model.NutritionTotals

/**
 * Row shape of [MealDao.getAllSummaries]: everything a totals-only reader needs, without
 * [MealEntity.itemsJson].
 *
 * The eight total columns are written by `EditableMealDraft.toEntity`, which always sums them from
 * the draft's items, so they equal the item-derived totals by construction. That
 * invariant is what makes this projection a safe substitute for decoding the item JSON, and it is
 * pinned by `MealSummaryTotalsTest`.
 */
data class MealSummary(
    val id: Long,
    val createdAtEpochMillis: Long,
    val title: String,
    val category: String,
    val caloriesKcal: Double?,
    val amountGml: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val saltG: Double?,
)

/** The persisted totals of this row, read straight off the columns. */
fun MealSummary.toNutritionTotals(): NutritionTotals = NutritionTotals(
    caloriesKcal = caloriesKcal,
    amountGml = amountGml,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    saturatedFatG = saturatedFatG,
    sugarG = sugarG,
    saltG = saltG,
)

/** An unknown or legacy persisted category name resolves neutrally, exactly as in [toSavedMeal]. */
fun MealSummary.mealCategory(): MealCategory =
    runCatching { MealCategory.valueOf(category) }.getOrDefault(MealCategory.OTHER)

/**
 * The same projection taken in memory, so the invariant test and the repository fakes can produce a
 * summary without a database.
 */
fun MealEntity.toMealSummary(): MealSummary = MealSummary(
    id = id,
    createdAtEpochMillis = createdAtEpochMillis,
    title = title,
    category = category,
    caloriesKcal = caloriesKcal,
    amountGml = amountGml,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    saturatedFatG = saturatedFatG,
    sugarG = sugarG,
    saltG = saltG,
)
