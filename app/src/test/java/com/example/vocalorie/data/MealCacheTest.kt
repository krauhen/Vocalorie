package com.example.vocalorie.data

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.MealCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the dedicated meal/item cache: exact whole-meal matching (B3), separation from history
 * (B4), per-item normalization to 100 g/ml and scaling (F1), and the reviewed-save write policy.
 */
class MealCacheTest {

    // --- Exact whole-meal matching (B3) ---

    @Test
    fun identicalQueryReusesCachedMeal() {
        val cache = listOf(mealDraft(query = "Buttermilch 200g").toCachedMealEntity()!!)

        val match = findCachedMealMatch(cache, "Buttermilch 200g")

        assertNotNull(match)
    }

    @Test
    fun wordOrderDoesNotAffectMatch() {
        val cache = listOf(mealDraft(query = "Apfel und Banane").toCachedMealEntity()!!)

        val match = findCachedMealMatch(cache, "Banane und Apfel")

        assertNotNull(match)
    }

    @Test
    fun subsetQueryDoesNotMatch() {
        val cache = listOf(mealDraft(query = "Buttermilch mit Honig").toCachedMealEntity()!!)

        val match = findCachedMealMatch(cache, "Buttermilch")

        assertNull(match)
    }

    @Test
    fun itemNameDoesNotTriggerMealMatch() {
        val cache = listOf(
            mealDraft(query = "Frühstück", itemName = "Froobie").toCachedMealEntity()!!,
        )

        val match = findCachedMealMatch(cache, "Froobie")

        assertNull(match)
    }

    @Test
    fun cacheMatchScalesToRequestedAmount() {
        val cache = listOf(
            mealDraft(query = "Buttermilch 100g", amountGml = 100.0, caloriesKcal = 50.0)
                .toCachedMealEntity()!!,
        )

        val match = findCachedMealMatch(cache, "Buttermilch 200g")

        assertNotNull(match)
        assertEquals("200", match!!.draft.amountGml)
        assertEquals("100", match.draft.caloriesKcal)
    }

    @Test
    fun reusedCachedMealScalesItsItemQuantityLabels() {
        val cache = listOf(
            mealDraft(query = "Buttermilch 200g", itemQuantity = "200 g", amountGml = 200.0)
                .toCachedMealEntity()!!,
        )

        val match = findCachedMealMatch(cache, "Buttermilch 100g")

        assertNotNull(match)
        assertEquals("100 g", match!!.draft.items.single().quantity)
        assertEquals("100", match.draft.items.single().amountGml)
    }

    @Test
    fun reusedCachedMealDerivesADescriptiveQuantityFromTheScaledAmount() {
        val cache = listOf(
            mealDraft(query = "Buttermilch 100g", itemQuantity = "eine Handvoll", amountGml = 100.0)
                .toCachedMealEntity()!!,
        )

        val match = findCachedMealMatch(cache, "Buttermilch 50g")

        assertNotNull(match)
        assertEquals("50 g", match!!.draft.items.single().quantity)
        assertEquals("50", match.draft.items.single().amountGml)
    }

    // --- Separation from history (B4) & write policy ---

    @Test
    fun repeatedSameFoodCollapsesToOneCacheKey() {
        // Five logged instances of the same food normalize to the same cache key, so upsert keeps
        // one row per key while history retains every instance separately.
        val keys = (1..5).map { mealDraft(query = "Buttermilch").toCachedMealEntity()!!.normalizedKey }

        assertEquals(1, keys.toSet().size)
        assertEquals("buttermilch", keys.first())
    }

    @Test
    fun reSaveOfSameKeyOverwritesInPlace() {
        val first = mealDraft(query = "Buttermilch 200g", caloriesKcal = 100.0).toCachedMealEntity()!!
        val second = mealDraft(query = "Buttermilch 200g", caloriesKcal = 120.0).toCachedMealEntity()!!

        // Same primary key -> REPLACE upsert keeps a single row; last save wins.
        assertEquals(first.normalizedKey, second.normalizedKey)
        assertTrue(first != second)
    }

    @Test
    fun amountOnlyQueryProducesNoCacheEntry() {
        assertNull(mealDraft(query = "200g").toCachedMealEntity())
    }

    // --- Per-item cache normalized to 100 g/ml (F1) ---

    @Test
    fun itemNutritionIsStoredPer100() {
        val draft = mealDraft(query = "Buttermilch 200g", itemName = "Buttermilch", itemAmountGml = 200.0, itemCaloriesKcal = 100.0)

        val item = draft.toCachedItemEntities().single()

        assertEquals("buttermilch", item.normalizedName)
        assertEquals(50.0, item.caloriesKcalPer100!!, 1e-9)
    }

    @Test
    fun itemWithoutAmountIsNotCached() {
        val draft = mealDraft(query = "Buttermilch", itemName = "Buttermilch", itemAmountGml = null, itemCaloriesKcal = 100.0)

        assertTrue(draft.toCachedItemEntities().isEmpty())
    }

    @Test
    fun reusedItemScalesToRequestedAmount() {
        val cached = mealDraft(query = "Buttermilch 200g", itemName = "Buttermilch", itemAmountGml = 200.0, itemCaloriesKcal = 100.0)
            .toCachedItemEntities()

        // A fresh estimate item "Buttermilch 150g" with a placeholder nutrition value.
        val estimate = mealDraft(query = "Buttermilch 150g", itemName = "Buttermilch", itemAmountGml = 150.0, itemCaloriesKcal = 999.0)

        val resolved = estimate.withItemsResolvedFromCache(cached)

        // 50 kcal/100g scaled to 150g = 75 kcal, amount preserved.
        assertEquals("75", resolved.items.single().caloriesKcal)
        assertEquals("150", resolved.items.single().amountGml)
        assertEquals("75", resolved.caloriesKcal)
    }

    @Test
    fun itemMatchIgnoresAmountToken() {
        val cached = mealDraft(query = "Buttermilch 200g", itemName = "Buttermilch 200 ml", itemAmountGml = 200.0, itemCaloriesKcal = 100.0)
            .toCachedItemEntities()

        val estimate = mealDraft(query = "x", itemName = "Buttermilch 150 ml", itemAmountGml = 150.0, itemCaloriesKcal = 999.0)

        val resolved = estimate.withItemsResolvedFromCache(cached)

        assertEquals("75", resolved.items.single().caloriesKcal)
    }

    @Test
    fun unknownItemIsNotResolvedFromCache() {
        val cached = mealDraft(query = "Buttermilch 200g", itemName = "Buttermilch", itemAmountGml = 200.0, itemCaloriesKcal = 100.0)
            .toCachedItemEntities()

        val estimate = mealDraft(query = "Apfel 150g", itemName = "Apfel", itemAmountGml = 150.0, itemCaloriesKcal = 80.0)

        val resolved = estimate.withItemsResolvedFromCache(cached)

        // Untouched: keeps its freshly estimated value.
        assertEquals("80", resolved.items.single().caloriesKcal)
    }

    // --- Food-type category survives the cache (regression: every cache hit downgraded to OTHER) ---

    @Test
    fun cachedMealPreservesItsFoodTypeCategory() {
        val entry = mealDraft(query = "Buttermilch 200g", category = MealCategory.DRINK).toCachedMealEntity()!!

        assertEquals("DRINK", entry.category)
        assertEquals(MealCategory.DRINK, entry.toSavedMeal().category)
    }

    @Test
    fun cacheHitReusesTheOriginalCategoryInsteadOfDowngradingToOther() {
        val cache = listOf(mealDraft(query = "Buttermilch 200g", category = MealCategory.DRINK).toCachedMealEntity()!!)

        val match = findCachedMealMatch(cache, "Buttermilch 200g")

        assertNotNull(match)
        assertEquals(MealCategory.DRINK, match!!.meal.category)
        assertEquals(MealCategory.DRINK, match.draft.category)
    }

    @Test
    fun everyCategoryRoundTripsThroughTheCacheUnchanged() {
        MealCategory.entries.forEach { category ->
            val entry = mealDraft(query = "Buttermilch 200g", category = category).toCachedMealEntity()!!

            assertEquals(category, entry.toSavedMeal().category)
        }
    }

    @Test
    fun preExistingCacheEntryWithoutCategoryFallsBackToOther() {
        // A row written before the category column existed takes the additive default.
        val legacyEntry = CachedMealEntity(
            normalizedKey = "buttermilch",
            title = "Buttermilch",
            query = "Buttermilch",
            itemsJson = "[]",
            assumptionsText = "",
            warningsText = "",
            confidence = "LOW",
            needsHumanReview = false,
        )

        assertEquals("OTHER", legacyEntry.category)
        assertEquals(MealCategory.OTHER, legacyEntry.toSavedMeal().category)
    }

    @Test
    fun unknownCachedCategoryNameFallsBackToOther() {
        val entry = mealDraft(query = "Buttermilch 200g").toCachedMealEntity()!!.copy(category = "BRUNCH")

        assertEquals(MealCategory.OTHER, entry.toSavedMeal().category)
    }

    private fun mealDraft(
        query: String,
        itemName: String = query,
        itemQuantity: String = query,
        amountGml: Double = 100.0,
        caloriesKcal: Double = 10.0,
        itemAmountGml: Double? = amountGml,
        itemCaloriesKcal: Double = caloriesKcal,
        category: MealCategory = MealCategory.OTHER,
    ): EditableMealDraft = EditableMealDraft(
        title = "",
        query = query,
        items = listOf(
            EditableFoodItem(
                name = itemName,
                quantity = itemQuantity,
                amountGml = itemAmountGml?.toEdit() ?: "",
                caloriesKcal = itemCaloriesKcal.toEdit(),
                proteinG = "",
                carbsG = "",
                fatG = "",
                saturatedFatG = "",
                sugarG = "",
                saltG = "",
                source = "",
                reasoning = "",
            ),
        ),
        caloriesKcal = "",
        amountGml = "",
        proteinG = "",
        carbsG = "",
        fatG = "",
        saturatedFatG = "",
        sugarG = "",
        saltG = "",
        assumptionsText = "",
        warningsText = "",
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = false,
        category = category,
    )

    private fun Double.toEdit(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
}
