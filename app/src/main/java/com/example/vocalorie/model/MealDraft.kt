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
    amountGml = items.sumOfEditable { it.amountGml }.toEditableNumberText(),
    caloriesKcal = items.sumOfEditable { it.caloriesKcal }.toEditableNumberText(),
    proteinG = items.sumOfEditable { it.proteinG }.toEditableNumberText(),
    carbsG = items.sumOfEditable { it.carbsG }.toEditableNumberText(),
    fatG = items.sumOfEditable { it.fatG }.toEditableNumberText(),
    saturatedFatG = items.sumOfEditable { it.saturatedFatG }.toEditableNumberText(),
    sugarG = items.sumOfEditable { it.sugarG }.toEditableNumberText(),
    saltG = items.sumOfEditable { it.saltG }.toEditableNumberText(),
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
        val scaledAmount = item.amountGml.scaledEditableNumber(factor)
        item.copy(
            quantity = item.quantity.scaledQuantityLabel(factor, scaledAmount),
            amountGml = scaledAmount,
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
    return number.multiply(factor).toEditableNumberText()
}

/**
 * Scales a display label such as `"2 eggs"` or `"1 Scheibe"`: the leading number moves, the
 * trailing words stay verbatim. A label with no leading number is replaced by one derived from the
 * already-scaled [scaledAmountGml], whose unit is inferred from the original text — the model
 * carries no unit for `amountGml`. With nothing to scale and nothing to derive from, the label is
 * left alone rather than blanked.
 */
private fun String.scaledQuantityLabel(factor: BigDecimal, scaledAmountGml: String): String {
    val trimmed = trim()
    val leadingNumber = quantityLabelNumberPattern.find(trimmed)
    if (leadingNumber != null) {
        val number = leadingNumber.value.toEditableBigDecimalOrNull()
        if (number != null) {
            val scaled = number.multiply(factor).setScale(1, RoundingMode.HALF_UP)
            return scaled.toEditableNumberText() + trimmed.substring(leadingNumber.value.length)
        }
    }
    val amount = scaledAmountGml.toPositiveEditableBigDecimalOrNull() ?: return this
    return "${amount.toEditableNumberText()} ${inferredQuantityUnit()}"
}

private fun String.inferredQuantityUnit(): String =
    if (quantityLabelMilliliterPattern.containsMatchIn(this)) "ml" else "g"

private val quantityLabelNumberPattern = Regex("""^[+-]?\d+(?:[.,]\d+)?""")

/** Matches `ml` or `l` only as a standalone token, so "Milch" and "Salat" do not read as litres. */
private val quantityLabelMilliliterPattern = Regex("""(?<![\p{L}\d])m?l(?![\p{L}\d])""", RegexOption.IGNORE_CASE)

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
