package com.example.vocalorie.model

import java.math.BigDecimal
import java.math.RoundingMode

data class EditableMealDraft(
    val title: String,
    val query: String,
    val items: List<EditableFoodItem>,
    val caloriesKcal: String,
    val amountGml: String,
    val proteinG: String,
    val carbsG: String,
    val fatG: String,
    val saturatedFatG: String,
    val sugarG: String,
    val saltG: String,
    val assumptionsText: String,
    val warningsText: String,
    val confidence: ConfidenceLevel,
    val needsHumanReview: Boolean,
    val createdAtEpochMillis: Long? = null,
    val category: MealCategory = MealCategory.OTHER,
)

data class EditableFoodItem(
    val name: String,
    val quantity: String,
    val amountGml: String,
    val caloriesKcal: String,
    val proteinG: String,
    val carbsG: String,
    val fatG: String,
    val saturatedFatG: String,
    val sugarG: String,
    val saltG: String,
    val source: String,
    val reasoning: String,
)

data class SavedMeal(
    val id: Long,
    val createdAtEpochMillis: Long,
    val title: String = "",
    val query: String,
    val items: List<FoodItemEstimate>,
    val totals: NutritionTotals,
    val assumptions: List<String>,
    val warnings: List<String>,
    val confidence: ConfidenceLevel,
    val needsHumanReview: Boolean,
    val category: MealCategory = MealCategory.OTHER,
)

fun EditableMealDraft.withTotalsSummedFromItems(): EditableMealDraft = copy(
    amountGml = items.sumOfEditable { it.amountGml }.toEditText(),
    caloriesKcal = items.sumOfEditable { it.caloriesKcal }.toEditText(),
    proteinG = items.sumOfEditable { it.proteinG }.toEditText(),
    carbsG = items.sumOfEditable { it.carbsG }.toEditText(),
    fatG = items.sumOfEditable { it.fatG }.toEditText(),
    saturatedFatG = items.sumOfEditable { it.saturatedFatG }.toEditText(),
    sugarG = items.sumOfEditable { it.sugarG }.toEditText(),
    saltG = items.sumOfEditable { it.saltG }.toEditText(),
)

fun EditableMealDraft.withItemsScaledByPortion(recipeMakes: String, ate: String): EditableMealDraft? {
    val factor = portionScaleFactor(recipeMakes = recipeMakes, ate = ate) ?: return null
    return withItemsScaledByFactor(factor)
}

fun EditableMealDraft.withItemsScaledByPortionFromBaseline(
    recipeMakes: String,
    ate: String,
    baselineItems: List<EditableFoodItem>,
): EditableMealDraft? {
    val factor = portionScaleFactor(recipeMakes = recipeMakes, ate = ate) ?: return null
    return copy(items = baselineItems).withItemsScaledByFactor(factor)
}

fun portionScaleFactor(recipeMakes: String, ate: String): BigDecimal? {
    val recipeMakesValue = recipeMakes.toPositiveEditableBigDecimalOrNull() ?: return null
    val ateValue = ate.toPositiveEditableBigDecimalOrNull() ?: return null
    return ateValue.divide(recipeMakesValue, 12, RoundingMode.HALF_UP).stripTrailingZeros()
}

fun EditableMealDraft.withItemsScaledByFactor(factor: BigDecimal): EditableMealDraft = copy(
    items = items.map { item ->
        item.copy(
            amountGml = item.amountGml.scaledEditableNumber(factor),
            caloriesKcal = item.caloriesKcal.scaledEditableNumber(factor),
            proteinG = item.proteinG.scaledEditableNumber(factor),
            carbsG = item.carbsG.scaledEditableNumber(factor),
            fatG = item.fatG.scaledEditableNumber(factor),
            saturatedFatG = item.saturatedFatG.scaledEditableNumber(factor),
            sugarG = item.sugarG.scaledEditableNumber(factor),
            saltG = item.saltG.scaledEditableNumber(factor),
        )
    },
).withTotalsSummedFromItems()

private fun List<EditableFoodItem>.sumOfEditable(selector: (EditableFoodItem) -> String): BigDecimal =
    fold(BigDecimal.ZERO) { total, item -> total + selector(item).toEditableBigDecimalOrZero() }

private fun String.scaledEditableNumber(factor: BigDecimal): String {
    val number = toEditableBigDecimalOrNull() ?: return this
    return number.multiply(factor).toEditText()
}

private fun String.toEditableBigDecimalOrZero(): BigDecimal = trim()
    .replace(',', '.')
    .takeIf { it.isNotEmpty() }
    ?.let { value -> runCatching { BigDecimal(value) }.getOrNull() }
    ?: BigDecimal.ZERO

private fun String.toPositiveEditableBigDecimalOrNull(): BigDecimal? =
    toEditableBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

private fun String.toEditableBigDecimalOrNull(): BigDecimal? = trim()
    .replace(',', '.')
    .takeIf { it.isNotEmpty() }
    ?.let { value -> runCatching { BigDecimal(value) }.getOrNull() }

private fun BigDecimal.toEditText(): String = stripTrailingZeros().toPlainString()
