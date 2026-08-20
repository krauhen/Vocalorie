package com.example.vocalorie.data

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.FoodItemEstimate
import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.model.NutritionAgentResult
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.model.toEditableNumberTextOrEmpty
import com.example.vocalorie.model.withTotalsSummedFromItems
import com.example.vocalorie.model.withItemsScaledByFactor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

private val mealJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val foodItemListSerializer = ListSerializer(FoodItemEstimate.serializer())

/**
 * Recorded in a meal's warnings when its persisted `itemsJson` cannot be decoded, so an unreadable
 * breakdown is visible as such instead of silently presenting as a real 0-kcal meal.
 */
const val UNREADABLE_MEAL_ITEMS_WARNING: String =
    "Saved item details could not be read; showing the stored totals only."

fun NutritionAgentResult.toEditableDraft(): EditableMealDraft = EditableMealDraft(
    title = title,
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
    assumptionsText = assumptions.joinToString("\n"),
    warningsText = warnings.joinToString("\n"),
    confidence = confidence,
    needsHumanReview = needsHumanReview,
    createdAtEpochMillis = null,
    category = category,
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
        assumptionsText = assumptionsText.trim(),
        warningsText = warningsText.trim(),
        confidence = confidence.name,
        needsHumanReview = needsHumanReview,
        category = category.name,
    )
}

fun EditableMealDraft.toEntity(id: Long, createdAtEpochMillis: Long): MealEntity =
    toEntity(createdAtEpochMillis = createdAtEpochMillis).copy(id = id)

fun MealEntity.toSavedMeal(): SavedMeal {
    // A decode failure must not turn into a real-looking 0-kcal meal: fall back to the totals stored
    // on the row and record that the per-item breakdown is unreadable.
    val decodedItems = runCatching { mealJson.decodeFromString(foodItemListSerializer, itemsJson) }.getOrNull()
    return SavedMeal(
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        title = title,
        query = query,
        items = decodedItems.orEmpty(),
        totals = decodedItems?.toSummedNutritionTotals() ?: toStoredNutritionTotals(),
        assumptions = assumptionsText.toLinesList(),
        warnings = warningsText.toLinesList().plusUnreadableItemsWarningIf(decodedItems == null),
        confidence = runCatching { ConfidenceLevel.valueOf(confidence) }.getOrDefault(ConfidenceLevel.LOW),
        needsHumanReview = needsHumanReview,
        category = category.toMealCategoryOrOther(),
    )
}

fun SavedMeal.toEditableDraft(): EditableMealDraft = EditableMealDraft(
    title = title.resolveMealTitle(query, items.firstOrNull()?.name),
    query = query,
    items = items.map { it.toEditableFoodItem() },
    caloriesKcal = totals.caloriesKcal.toEditableNumberTextOrEmpty(),
    amountGml = totals.amountGml.toEditableNumberTextOrEmpty(),
    proteinG = totals.proteinG.toEditableNumberTextOrEmpty(),
    carbsG = totals.carbsG.toEditableNumberTextOrEmpty(),
    fatG = totals.fatG.toEditableNumberTextOrEmpty(),
    saturatedFatG = totals.saturatedFatG.toEditableNumberTextOrEmpty(),
    sugarG = totals.sugarG.toEditableNumberTextOrEmpty(),
    saltG = totals.saltG.toEditableNumberTextOrEmpty(),
    assumptionsText = assumptions.joinToString("\n"),
    warningsText = warnings.joinToString("\n"),
    confidence = confidence,
    needsHumanReview = needsHumanReview,
    createdAtEpochMillis = createdAtEpochMillis,
    category = category,
).withTotalsSummedFromItems()

/**
 * Reuse a cached whole meal only when the request's normalized query key exactly equals a
 * cached meal-key entry (order-insensitive token-set equality, amount tokens stripped, item
 * names excluded). Reads the dedicated meal-key cache, never the meals history list.
 */
fun findCachedMealMatch(cachedMeals: List<CachedMealEntity>, requestQuery: String): CachedMealMatch? {
    val normalizedRequestKey = requestQuery.toStableNormalizedMealKey()
    if (normalizedRequestKey.isBlank()) return null

    val entry = cachedMeals.firstOrNull { it.normalizedKey == normalizedRequestKey } ?: return null
    return entry.toCachedMealMatch(requestQuery)
}

/**
 * Turn an already-matched cache row into the reusable match, scaled to the request's amount. This is
 * the half of [findCachedMealMatch] that stays useful once the row is found by a keyed query instead
 * of a scan; matching itself is `normalizedKey == requestQuery.toStableNormalizedMealKey()`.
 */
fun CachedMealEntity.toCachedMealMatch(requestQuery: String): CachedMealMatch {
    val meal = toSavedMeal()
    return CachedMealMatch(meal = meal, draft = meal.toPreparedCachedDraft(requestQuery))
}

/**
 * The distinct normalized item-name keys this draft would look up in the item-name cache, so a
 * caller can query exactly those names instead of reading the whole table.
 */
fun EditableMealDraft.cachedItemNameKeys(): List<String> = items
    .map { it.name.toStableNormalizedMealKey() }
    .filter { it.isNotBlank() }
    .distinct()

/** Decode a cached meal entry back into a [SavedMeal] for reuse (synthetic id/timestamp). */
fun CachedMealEntity.toSavedMeal(): SavedMeal {
    val decodedItems = runCatching { mealJson.decodeFromString(foodItemListSerializer, itemsJson) }.getOrNull()
    return SavedMeal(
        id = 0L,
        createdAtEpochMillis = 0L,
        title = title,
        query = query,
        items = decodedItems.orEmpty(),
        totals = decodedItems.orEmpty().toSummedNutritionTotals(),
        assumptions = assumptionsText.toLinesList(),
        warnings = warningsText.toLinesList().plusUnreadableItemsWarningIf(decodedItems == null),
        confidence = runCatching { ConfidenceLevel.valueOf(confidence) }.getOrDefault(ConfidenceLevel.LOW),
        needsHumanReview = needsHumanReview,
        category = category.toMealCategoryOrOther(),
    )
}

/**
 * Build the meal-key cache row for a reviewed meal. Returns null when the query has no usable
 * normalized key (e.g. only amount/number tokens), so nothing is cached under a blank key.
 */
fun EditableMealDraft.toCachedMealEntity(): CachedMealEntity? {
    val normalizedKey = query.toStableNormalizedMealKey()
    if (normalizedKey.isBlank()) return null
    return CachedMealEntity(
        normalizedKey = normalizedKey,
        title = title.resolveMealTitle(query, items.firstOrNull()?.name),
        query = query.trim(),
        itemsJson = mealJson.encodeToString(foodItemListSerializer, items.map { it.toFoodItemEstimate() }),
        assumptionsText = assumptionsText.trim(),
        warningsText = warningsText.trim(),
        confidence = confidence.name,
        needsHumanReview = needsHumanReview,
        category = category.name,
    )
}

/**
 * Build item-name cache rows for a reviewed meal, one per item, storing nutrition per 100 g/ml.
 * Items without a usable name or a positive amount (can't be normalized to a 100 basis) are skipped.
 */
fun EditableMealDraft.toCachedItemEntities(): List<CachedItemEntity> =
    items.mapNotNull { it.toFoodItemEstimate().toCachedItemEntity() }

private fun FoodItemEstimate.toCachedItemEntity(): CachedItemEntity? {
    val normalizedName = name.toStableNormalizedMealKey()
    if (normalizedName.isBlank()) return null
    val amount = amountGml?.takeIf { it > 0.0 } ?: return null
    val per100Factor = 100.0 / amount
    return CachedItemEntity(
        normalizedName = normalizedName,
        displayName = name.trim(),
        caloriesKcalPer100 = caloriesKcal?.times(per100Factor),
        proteinGPer100 = proteinG?.times(per100Factor),
        carbsGPer100 = carbsG?.times(per100Factor),
        fatGPer100 = fatG?.times(per100Factor),
        saturatedFatGPer100 = saturatedFatG?.times(per100Factor),
        sugarGPer100 = sugarG?.times(per100Factor),
        saltGPer100 = saltG?.times(per100Factor),
        source = source,
        reasoning = reasoning,
    )
}

/**
 * After an estimate produces items, auto-resolve any item whose normalized name matches an
 * item-cache entry, replacing its nutrition with the cached per-100 values scaled to that item's
 * requested amount. Items with no cache match or no positive amount are left untouched.
 */
fun EditableMealDraft.withItemsResolvedFromCache(cachedItems: List<CachedItemEntity>): EditableMealDraft {
    if (cachedItems.isEmpty()) return this
    val cacheByName = cachedItems.associateBy { it.normalizedName }
    var changed = false
    val resolvedItems = items.map { item ->
        val cached = cacheByName[item.name.toStableNormalizedMealKey()] ?: return@map item
        val amount = item.amountGml.toNullableDouble()?.takeIf { it > 0.0 } ?: return@map item
        changed = true
        cached.scaledEditableFoodItem(item, amount)
    }
    if (!changed) return this
    return copy(items = resolvedItems).withTotalsSummedFromItems()
}

private fun CachedItemEntity.scaledEditableFoodItem(item: EditableFoodItem, requestedAmountGml: Double): EditableFoodItem {
    val factor = requestedAmountGml / 100.0
    return item.copy(
        caloriesKcal = caloriesKcalPer100?.times(factor).toEditableNumberTextOrEmpty(),
        proteinG = proteinGPer100?.times(factor).toEditableNumberTextOrEmpty(),
        carbsG = carbsGPer100?.times(factor).toEditableNumberTextOrEmpty(),
        fatG = fatGPer100?.times(factor).toEditableNumberTextOrEmpty(),
        saturatedFatG = saturatedFatGPer100?.times(factor).toEditableNumberTextOrEmpty(),
        sugarG = sugarGPer100?.times(factor).toEditableNumberTextOrEmpty(),
        saltG = saltGPer100?.times(factor).toEditableNumberTextOrEmpty(),
        source = source.toSourceUrlOrBlank(),
        reasoning = reasoning,
    )
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
    amountGml = amountGml.toEditableNumberTextOrEmpty(),
    caloriesKcal = caloriesKcal.toEditableNumberTextOrEmpty(),
    proteinG = proteinG.toEditableNumberTextOrEmpty(),
    carbsG = carbsG.toEditableNumberTextOrEmpty(),
    fatG = fatG.toEditableNumberTextOrEmpty(),
    saturatedFatG = saturatedFatG.toEditableNumberTextOrEmpty(),
    sugarG = sugarG.toEditableNumberTextOrEmpty(),
    saltG = saltG.toEditableNumberTextOrEmpty(),
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

/** Unknown or legacy category names resolve to the neutral fallback rather than failing. */
private fun String.toMealCategoryOrOther(): MealCategory =
    runCatching { MealCategory.valueOf(this) }.getOrDefault(MealCategory.OTHER)

private fun List<String>.plusUnreadableItemsWarningIf(unreadable: Boolean): List<String> =
    if (unreadable && UNREADABLE_MEAL_ITEMS_WARNING !in this) this + UNREADABLE_MEAL_ITEMS_WARNING else this

/**
 * The totals persisted on the meal row itself, used when [MealEntity.itemsJson] cannot be decoded so
 * the meal keeps its real numbers instead of collapsing to zero.
 */
private fun MealEntity.toStoredNutritionTotals(): NutritionTotals = NutritionTotals(
    caloriesKcal = caloriesKcal,
    amountGml = amountGml,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    saturatedFatG = saturatedFatG,
    sugarG = sugarG,
    saltG = saltG,
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

private fun String.normalizeMealText(): String = foldedForKey()
    .replace(countPrefixedAmountPattern, " ")
    .replace(amountTokenPattern, " ")
    .replace(kcalTokenPattern, " ")
    .replace(Regex("\\s+"), " ")
    .trim()

fun String.toStableNormalizedMealKey(): String = normalizeMealText()
    .split(Regex("[^\\p{L}\\p{Nd}]+"))
    .asSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .filterNot { it.isNumericToken() }
    .filterNot { it in COUNTING_WORDS }
    .filterNot { it in UNIT_WORDS }
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

/** A `<count>x<amount><unit>` token such as `2x106g`, stripped whole before [amountTokenPattern]. */
private val countPrefixedAmountPattern =
    Regex("""\b\d+(?:[.,]\d+)?\s*[x\u00D7]\s*\d+(?:[.,]\d+)?\s*(?:g|ml|kg|l)\b""", RegexOption.IGNORE_CASE)

/** A spoken calorie guess such as `169kcal`, size rather than identity, so stripped like an amount. */
private val kcalTokenPattern = Regex("""\b\d+(?:[.,]\d+)?\s*kcal\b""", RegexOption.IGNORE_CASE)

/**
 * Fold a query into the key's character space: lowercase, German umlauts and eszett to their
 * digraphs, then drop any remaining combining marks. The order matters — NFD alone would turn
 * `Muesli` into `Musli`, which no longer matches the `Muesli` the same user also says.
 */
internal fun String.foldedForKey(): String = java.text.Normalizer.normalize(trim(), java.text.Normalizer.Form.NFC)
    .lowercase()
    .replace("\u00e4", "ae")
    .replace("\u00f6", "oe")
    .replace("\u00fc", "ue")
    .replace("\u00df", "ss")
    .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) }
    .replace(combiningMarkPattern, "")

private val combiningMarkPattern = Regex("""\p{Mn}+""")

/**
 * German counting words in their common inflections. A count states how much, and how much already
 * lives in the amount, so a count never contributes to a key. Written already folded.
 */
internal val COUNTING_WORDS: Set<String> = setOf(
    "ein", "eine", "einen", "einem", "eins",
    "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwoelf",
)

/**
 * Unit words the system prompt teaches the model to expect, plus the drink containers. Like counts,
 * they are size and not identity, so "ein Glas Buttermilch" keys the same as "Buttermilch".
 * Written already folded.
 */
internal val UNIT_WORDS: Set<String> = setOf(
    "g", "ml", "kg", "l",
    "el", "tl", "stueck", "scheibe", "scheiben", "prise", "portion", "glas", "becher", "tasse",
)
