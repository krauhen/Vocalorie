package com.example.vocalorie.data

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.model.NutritionTotals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the invariant that makes [MealDao.getAllSummaries] a safe substitute for decoding
 * `itemsJson`: a meal's eight persisted total columns equal the totals derived from its items.
 *
 * It holds by construction, because `toEntity` always runs `withTotalsSummedFromItems()` — but only
 * for rows this build writes. Pinning it here means a future change that stops summing the totals
 * (or writes a draft's stale typed totals instead) fails a test rather than silently skewing
 * statistics.
 */
class MealSummaryTotalsTest {

    @Test
    fun persistedTotalsEqualItemDerivedTotalsForASingleItemMeal() {
        val entity = draft(items = listOf(item(amountGml = 200.0, caloriesKcal = 96.0, proteinG = 6.8))).toEntity()

        assertTotalsMatch(entity)
    }

    @Test
    fun persistedTotalsEqualItemDerivedTotalsForAMultiItemMeal() {
        val entity = draft(
            items = listOf(
                item(name = "Buttermilch", amountGml = 200.0, caloriesKcal = 96.0, proteinG = 6.8, carbsG = 8.2),
                item(name = "Honig", amountGml = 20.0, caloriesKcal = 61.0, carbsG = 16.4, sugarG = 16.2),
                item(name = "Haferflocken", amountGml = 40.0, caloriesKcal = 148.0, proteinG = 5.4, fatG = 2.8, saltG = 0.01),
            ),
        ).toEntity()

        assertTotalsMatch(entity)
        // The summed values are the ones a totals-only reader gets, so pin them concretely too.
        assertEquals(305.0, entity.toMealSummary().caloriesKcal!!, 1e-9)
        assertEquals(260.0, entity.toMealSummary().amountGml!!, 1e-9)
    }

    @Test
    fun persistedTotalsIgnoreStaleTypedTotalsOnTheDraft() {
        // A draft can carry hand-typed totals that no longer match its items; the row must store the
        // item-derived numbers, which is exactly what the projection then reads back.
        val entity = draft(items = listOf(item(amountGml = 100.0, caloriesKcal = 42.0)))
            .copy(caloriesKcal = "9999", amountGml = "1")
            .toEntity()

        assertTotalsMatch(entity)
        assertEquals(42.0, entity.caloriesKcal!!, 1e-9)
    }

    @Test
    fun anItemlessMealPersistsZeroTotalsRatherThanNulls() {
        val entity = draft(items = emptyList()).toEntity()

        assertTotalsMatch(entity)
        assertEquals(0.0, entity.toMealSummary().caloriesKcal!!, 1e-9)
    }

    @Test
    fun theProjectionCarriesIdentityTimestampTitleAndCategoryButNoItemJson() {
        val entity = draft(items = listOf(item()), category = MealCategory.DRINK)
            .toEntity(id = 7L, createdAtEpochMillis = 1_700_000_000_000L)

        val summary = entity.toMealSummary()

        assertEquals(7L, summary.id)
        assertEquals(1_700_000_000_000L, summary.createdAtEpochMillis)
        assertEquals(entity.title, summary.title)
        assertEquals(MealCategory.DRINK, summary.mealCategory())
        // MealSummary has no itemsJson field at all; nothing on this path decodes item JSON.
        assertEquals(
            emptyList<String>(),
            MealSummary::class.java.declaredFields.map { it.name }.filter { it.contains("items", ignoreCase = true) },
        )
    }

    /** The persisted columns (via the projection) against the totals decoded from `itemsJson`. */
    private fun assertTotalsMatch(entity: MealEntity) {
        val persisted: NutritionTotals = entity.toMealSummary().toNutritionTotals()
        val itemDerived: NutritionTotals = entity.toSavedMeal().totals

        assertEquals(itemDerived.caloriesKcal!!, persisted.caloriesKcal!!, 1e-9)
        assertEquals(itemDerived.amountGml!!, persisted.amountGml!!, 1e-9)
        assertEquals(itemDerived.proteinG!!, persisted.proteinG!!, 1e-9)
        assertEquals(itemDerived.carbsG!!, persisted.carbsG!!, 1e-9)
        assertEquals(itemDerived.fatG!!, persisted.fatG!!, 1e-9)
        assertEquals(itemDerived.saturatedFatG!!, persisted.saturatedFatG!!, 1e-9)
        assertEquals(itemDerived.sugarG!!, persisted.sugarG!!, 1e-9)
        assertEquals(itemDerived.saltG!!, persisted.saltG!!, 1e-9)
    }

    private fun draft(
        items: List<EditableFoodItem>,
        category: MealCategory = MealCategory.MEAL,
    ): EditableMealDraft = EditableMealDraft(
        title = "",
        query = "Buttermilch mit Honig",
        items = items,
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

    private fun item(
        name: String = "Buttermilch",
        amountGml: Double = 100.0,
        caloriesKcal: Double = 40.0,
        proteinG: Double = 0.0,
        carbsG: Double = 0.0,
        fatG: Double = 0.0,
        saturatedFatG: Double = 0.0,
        sugarG: Double = 0.0,
        saltG: Double = 0.0,
    ): EditableFoodItem = EditableFoodItem(
        name = name,
        quantity = "1 Portion",
        amountGml = amountGml.toString(),
        caloriesKcal = caloriesKcal.toString(),
        proteinG = proteinG.toString(),
        carbsG = carbsG.toString(),
        fatG = fatG.toString(),
        saturatedFatG = saturatedFatG.toString(),
        sugarG = sugarG.toString(),
        saltG = saltG.toString(),
        source = "",
        reasoning = "",
    )
}
