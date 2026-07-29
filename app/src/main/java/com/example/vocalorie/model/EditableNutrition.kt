package com.example.vocalorie.model

/**
 * The eight editable nutrition text fields shared by a meal draft and a food item.
 *
 * Grouping them into one type keeps the editor callbacks single-argument, so a field can no
 * longer be transposed with its neighbour by a positional argument list. Every mapping below
 * uses named arguments for the same reason.
 */
data class EditableNutrition(
    val calories: String,
    val amount: String,
    val fat: String,
    val saturatedFat: String,
    val carbs: String,
    val sugar: String,
    val protein: String,
    val salt: String,
)

fun EditableFoodItem.toEditableNutrition(): EditableNutrition = EditableNutrition(
    calories = caloriesKcal,
    amount = amountGml,
    fat = fatG,
    saturatedFat = saturatedFatG,
    carbs = carbsG,
    sugar = sugarG,
    protein = proteinG,
    salt = saltG,
)

fun EditableFoodItem.withNutrition(nutrition: EditableNutrition): EditableFoodItem = copy(
    caloriesKcal = nutrition.calories,
    amountGml = nutrition.amount,
    fatG = nutrition.fat,
    saturatedFatG = nutrition.saturatedFat,
    carbsG = nutrition.carbs,
    sugarG = nutrition.sugar,
    proteinG = nutrition.protein,
    saltG = nutrition.salt,
)

fun EditableMealDraft.toEditableNutrition(): EditableNutrition = EditableNutrition(
    calories = caloriesKcal,
    amount = amountGml,
    fat = fatG,
    saturatedFat = saturatedFatG,
    carbs = carbsG,
    sugar = sugarG,
    protein = proteinG,
    salt = saltG,
)

fun EditableMealDraft.withNutrition(nutrition: EditableNutrition): EditableMealDraft = copy(
    caloriesKcal = nutrition.calories,
    amountGml = nutrition.amount,
    fatG = nutrition.fat,
    saturatedFatG = nutrition.saturatedFat,
    carbsG = nutrition.carbs,
    sugarG = nutrition.sugar,
    proteinG = nutrition.protein,
    saltG = nutrition.salt,
)
