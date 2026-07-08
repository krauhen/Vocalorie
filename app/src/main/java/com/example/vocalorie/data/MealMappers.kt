package com.example.vocalorie.data

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.FoodItemEstimate
import com.example.vocalorie.model.NutritionAgentResult
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.model.withTotalsSummedFromItems
import com.example.vocalorie.model.withItemsScaledByFactor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

private val mealJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val foodItemListSerializer = ListSerializer(FoodItemEstimate.serializer())

fun NutritionAgentResult.toEditableDraft(): EditableMealDraft = EditableMealDraft(
    title = query.toShortMealTitle(items),
    query = query,
    items = items.map { it.toEditableFoodItem() },
    caloriesKcal = "",
    amountGml = "",
    proteinG = "",
    carbsG = "",
    fatG = "",
    saturatedFatG = "",
    sugarG = "",
    saltG = "",
    source = listOf(source, *items.map { it.source }.toTypedArray()).firstConcreteSourceUrlOrBlank(),
    assumptionsText = assumptions.joinToString("\n"),
    warningsText = warnings.joinToString("\n"),
    confidence = confidence,
    needsHumanReview = needsHumanReview,
    createdAtEpochMillis = null,
).withTotalsSummedFromItems()

fun EditableMealDraft.toEntity(createdAtEpochMillis: Long = System.currentTimeMillis()): MealEntity {
    val draftWithComputedTotals = withTotalsSummedFromItems()
    return MealEntity(
        createdAtEpochMillis = createdAtEpochMillis,
        title = title.resolveMealTitle(query, items.firstOrNull()?.name),
        query = query.trim(),
        itemsJson = mealJson.encodeToString(foodItemListSerializer, items.map { it.toFoodItemEstimate() }),
        caloriesKcal = draftWithComputedTotals.caloriesKcal.toNullableDouble(),
        amountGml = draftWithComputedTotals.amountGml.toNullableDouble(),
        proteinG = draftWithComputedTotals.proteinG.toNullableDouble(),
        carbsG = draftWithComputedTotals.carbsG.toNullableDouble(),
        fatG = draftWithComputedTotals.fatG.toNullableDouble(),
        saturatedFatG = draftWithComputedTotals.saturatedFatG.toNullableDouble(),
        sugarG = draftWithComputedTotals.sugarG.toNullableDouble(),
        saltG = draftWithComputedTotals.saltG.toNullableDouble(),
        source = source.toSourceUrlOrBlank().ifBlank { null },
        assumptionsText = assumptionsText.trim(),
        warningsText = warningsText.trim(),
        confidence = confidence.name,
        needsHumanReview = needsHumanReview,
    )
}

fun EditableMealDraft.toEntity(id: Long, createdAtEpochMillis: Long): MealEntity =
    toEntity(createdAtEpochMillis = createdAtEpochMillis).copy(id = id)

fun MealEntity.toSavedMeal(): SavedMeal {
    val decodedItems = runCatching { mealJson.decodeFromString(foodItemListSerializer, itemsJson) }.getOrDefault(emptyList())
    return SavedMeal(
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        title = title,
        query = query,
        items = decodedItems,
        totals = decodedItems.toSummedNutritionTotals(),
        source = source.orEmpty().toSourceUrlOrBlank(),
        assumptions = assumptionsText.toLinesList(),
        warnings = warningsText.toLinesList(),
        confidence = runCatching { ConfidenceLevel.valueOf(confidence) }.getOrDefault(ConfidenceLevel.LOW),
        needsHumanReview = needsHumanReview,
    )
}

fun SavedMeal.toEditableDraft(): EditableMealDraft = EditableMealDraft(
    title = title.resolveMealTitle(query, items.firstOrNull()?.name),
    query = query,
    items = items.map { it.toEditableFoodItem() },
    caloriesKcal = totals.caloriesKcal.toEditText(),
    amountGml = totals.amountGml.toEditText(),
    proteinG = totals.proteinG.toEditText(),
    carbsG = totals.carbsG.toEditText(),
    fatG = totals.fatG.toEditText(),
    saturatedFatG = totals.saturatedFatG.toEditText(),
    sugarG = totals.sugarG.toEditText(),
    saltG = totals.saltG.toEditText(),
    source = source.toSourceUrlOrBlank(),
    assumptionsText = assumptions.joinToString("\n"),
    warningsText = warnings.joinToString("\n"),
    confidence = confidence,
    needsHumanReview = needsHumanReview,
    createdAtEpochMillis = createdAtEpochMillis,
).withTotalsSummedFromItems()

fun findCachedMealMatch(meals: List<SavedMeal>, requestQuery: String): CachedMealMatch? {
    val normalizedRequestKey = requestQuery.toStableNormalizedMealKey()
    if (normalizedRequestKey.isBlank()) return null

    val meal = meals.firstOrNull { it.matchesNormalizedMealKey(normalizedRequestKey) } ?: return null
    return CachedMealMatch(meal = meal, draft = meal.toPreparedCachedDraft(requestQuery))
}

fun searchSavedMeals(meals: List<SavedMeal>, searchQuery: String, limit: Int = 5): List<SavedMeal> {
    val normalizedSearchKey = searchQuery.toStableNormalizedMealKey()
    if (normalizedSearchKey.isBlank() || limit <= 0) return emptyList()

    return meals.asSequence()
        .filter { it.matchesNormalizedMealKey(normalizedSearchKey) }
        .take(limit)
        .toList()
}

private fun FoodItemEstimate.toEditableFoodItem(): EditableFoodItem = EditableFoodItem(
    name = name,
    quantity = quantity,
    amountGml = amountGml.toEditText(),
    caloriesKcal = caloriesKcal.toEditText(),
    proteinG = proteinG.toEditText(),
    carbsG = carbsG.toEditText(),
    fatG = fatG.toEditText(),
    saturatedFatG = saturatedFatG.toEditText(),
    sugarG = sugarG.toEditText(),
    saltG = saltG.toEditText(),
    source = source.toSourceUrlOrBlank(),
    reasoning = reasoning,
)

private fun EditableFoodItem.toFoodItemEstimate(): FoodItemEstimate = FoodItemEstimate(
    name = name.trim(),
    quantity = quantity.trim(),
    amountGml = amountGml.toNullableDouble(),
    caloriesKcal = caloriesKcal.toNullableDouble(),
    proteinG = proteinG.toNullableDouble(),
    carbsG = carbsG.toNullableDouble(),
    fatG = fatG.toNullableDouble(),
    saturatedFatG = saturatedFatG.toNullableDouble(),
    sugarG = sugarG.toNullableDouble(),
    saltG = saltG.toNullableDouble(),
    source = source.toSourceUrlOrBlank(),
    reasoning = reasoning.trim(),
)

private fun List<FoodItemEstimate>.toSummedNutritionTotals(): NutritionTotals = NutritionTotals(
    caloriesKcal = sumOf { it.caloriesKcal ?: 0.0 },
    amountGml = sumOf { it.amountGml ?: 0.0 },
    proteinG = sumOf { it.proteinG ?: 0.0 },
    carbsG = sumOf { it.carbsG ?: 0.0 },
    fatG = sumOf { it.fatG ?: 0.0 },
    saturatedFatG = sumOf { it.saturatedFatG ?: 0.0 },
    sugarG = sumOf { it.sugarG ?: 0.0 },
    saltG = sumOf { it.saltG ?: 0.0 },
)

private fun Double?.toEditText(): String = this?.let { value ->
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}.orEmpty()

private fun String.toShortMealTitle(items: List<FoodItemEstimate>): String =
    toShortMealTitle(items.firstOrNull()?.name)

private fun String.toShortMealTitle(firstItemName: String?): String {
    val trimmedItemName = firstItemName?.trim().orEmpty()
    return if (trimmedItemName.isNotBlank()) trimmedItemName else trim()
}

private fun String.resolveMealTitle(query: String, firstItemName: String?): String {
    val trimmedTitle = trim()
    val trimmedQuery = query.trim()
    if (trimmedTitle.isNotBlank() && !trimmedTitle.isEffectivelySameTextAs(trimmedQuery)) {
        return trimmedTitle
    }
    return trimmedQuery.toShortMealTitle(firstItemName)
}

private fun String.isEffectivelySameTextAs(other: String): Boolean =
    normalizeMealText() == other.normalizeMealText()

private fun String.normalizeMealText(): String = trim()
    .lowercase()
    .replace(amountTokenPattern, " ")
    .replace(Regex("\\s+"), " ")

private fun String.toStableNormalizedMealKey(): String = normalizeMealText()
    .split(Regex("[^\\p{L}\\p{Nd}]+"))
    .asSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .filterNot { it.isNumericToken() }
    .distinct()
    .sorted()
    .joinToString(" ")

private fun SavedMeal.matchesNormalizedMealKey(normalizedSearchKey: String): Boolean {
    val mealKeyTokens = searchableMealText().toStableNormalizedMealKey().split(' ').filter { it.isNotBlank() }
    val searchTokens = normalizedSearchKey.split(' ').filter { it.isNotBlank() }
    if (mealKeyTokens.isEmpty() || searchTokens.isEmpty()) return false

    return searchTokens.all { searchToken ->
        mealKeyTokens.any { mealToken -> mealToken.matchesSearchToken(searchToken) }
    }
}

private fun SavedMeal.searchableMealText(): String = buildString {
    append(title)
    append(' ')
    append(query)
    items.forEach { item ->
        append(' ')
        append(item.name)
    }
}

private fun SavedMeal.toPreparedCachedDraft(requestQuery: String): EditableMealDraft {
    val preparedDraft = toEditableDraft().copy(query = requestQuery.trim())
    val requestedAmount = requestQuery.extractRequestedAmountGmlOrNull()
    val baseAmount = totals.amountGml?.takeIf { it > 0.0 }
        ?: items.sumOf { it.amountGml ?: 0.0 }.takeIf { it > 0.0 }

    if (requestedAmount != null && baseAmount != null) {
        val factor = BigDecimal(requestedAmount).divide(BigDecimal(baseAmount), 12, RoundingMode.HALF_UP).stripTrailingZeros()
        return preparedDraft.withItemsScaledByFactor(factor)
    }

    return preparedDraft
}

private fun String.extractRequestedAmountGmlOrNull(): Double? {
    val match = requestAmountPattern.find(this) ?: return null
    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].lowercase()
    val multiplier = when (unit) {
        "g", "ml" -> 1.0
        "kg", "l" -> 1000.0
        else -> 1.0
    }
    return amount * multiplier
}

private fun String.isNumericToken(): Boolean = matches(numericTokenPattern)

private fun String.matchesSearchToken(searchToken: String): Boolean =
    this == searchToken || startsWith(searchToken) || searchToken.startsWith(this)

private fun List<String>.firstConcreteSourceUrlOrBlank(): String = asSequence()
    .map { it.toConcreteSourceUrlOrBlank() }
    .firstOrNull { it.isNotBlank() }
    .orEmpty()

private fun String.toNullableDouble(): Double? = trim()
    .replace(',', '.')
    .takeIf { it.isNotEmpty() }
    ?.toDoubleOrNull()

private fun String.toLinesList(): List<String> = lines().map { it.trim() }.filter { it.isNotEmpty() }

private fun String.toSourceUrlOrBlank(): String = toConcreteSourceUrlOrBlank()

private fun String.toConcreteSourceUrlOrBlank(): String = trim().takeIf { source ->
    (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) &&
        runCatching { java.net.URI(source) }.getOrNull()?.let { uri ->
            val path = uri.path.orEmpty().trim()
            path.isNotEmpty() && path != "/" || uri.query != null || uri.fragment != null
        } == true
}.orEmpty()

private val requestAmountPattern = Regex("""\b(\d+(?:[.,]\d+)?)\s*(g|ml|kg|l)\b""", RegexOption.IGNORE_CASE)
private val numericTokenPattern = Regex("""\d+(?:[.,]\d+)?""")
private val amountTokenPattern = Regex("""\b\d+(?:[.,]\d+)?\s*(?:g|ml|kg|l)\b""", RegexOption.IGNORE_CASE)
