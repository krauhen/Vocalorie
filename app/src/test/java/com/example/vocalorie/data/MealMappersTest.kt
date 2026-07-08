package com.example.vocalorie.data

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.FoodItemEstimate
import com.example.vocalorie.model.NutritionAgentResult
import com.example.vocalorie.model.NutritionTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MealMappersTest {
    @Test
    fun amountOnlyQueryUsesFirstItemNameAsEditableTitle() {
        val result = sampleResult().copy(
            query = "100g",
            items = listOf(
                sampleResult().items.single().copy(
                    name = "cucumber",
                    quantity = "100g",
                ),
            ),
        )

        val draft = result.toEditableDraft()

        assertEquals("cucumber", draft.title)
    }

    @Test
    fun promptLikeQueryDoesNotBecomeEditableTitleWhenItemNameIsKnown() {
        val result = sampleResult().copy(
            query = "Estimate this meal from the attached photo",
            items = listOf(
                sampleResult().items.single().copy(
                    name = "cucumber",
                    quantity = "100g",
                ),
            ),
        )

        val draft = result.toEditableDraft()

        assertEquals("cucumber", draft.title)
    }

    @Test
    fun genericHomepageSourceDoesNotOverrideSpecificItemSource() {
        val result = sampleResult().copy(
            source = "https://fdc.nal.usda.gov/",
            items = listOf(
                sampleResult().items.single().copy(
                    source = "https://fdc.nal.usda.gov/fdc-app.html#/food-details/12345/nutrients",
                ),
            ),
        )

        val draft = result.toEditableDraft()

        assertEquals("https://fdc.nal.usda.gov/fdc-app.html#/food-details/12345/nutrients", draft.source)
    }

    @Test
    fun agentResultBecomesEditableDraft() {
        val draft = sampleResult().copy(
            totals = NutritionTotals(
                caloriesKcal = 999.0,
                amountGml = 999.0,
                proteinG = 999.0,
                carbsG = 999.0,
                fatG = 999.0,
                saturatedFatG = 999.0,
                sugarG = 999.0,
                saltG = 999.0,
            ),
        ).toEditableDraft()

        assertEquals("large egg", draft.title)
        assertEquals("2 eggs", draft.query)
        assertEquals("150", draft.caloriesKcal)
        assertEquals("100", draft.amountGml)
        assertEquals("12.5", draft.proteinG)
        assertEquals("1", draft.carbsG)
        assertEquals("10", draft.fatG)
        assertEquals("3.2", draft.saturatedFatG)
        assertEquals("0.4", draft.sugarG)
        assertEquals("0.3", draft.saltG)
        assertEquals("https://fdc.nal.usda.gov/fdc-app.html#/food-details/748967/nutrients", draft.source)
        assertEquals("large egg", draft.items.single().name)
        assertEquals("100", draft.items.single().amountGml)
        assertEquals("3.2", draft.items.single().saturatedFatG)
        assertEquals("0.4", draft.items.single().sugarG)
        assertEquals("0.3", draft.items.single().saltG)
        assertEquals("https://fdc.nal.usda.gov/fdc-app.html#/food-details/748967/nutrients", draft.items.single().source)
        assertEquals("assumption one\nassumption two", draft.assumptionsText)
    }

    @Test
    fun editableDraftRoundTripsThroughEntityAndParsesBlankNumbersAsNull() {
        val draft = sampleResult().toEditableDraft().copy(caloriesKcal = "", fatG = "bad")
        val saved = draft.toEntity(createdAtEpochMillis = 123L).copy(id = 7L).toSavedMeal()

        assertEquals(7L, saved.id)
        assertEquals(123L, saved.createdAtEpochMillis)
        assertEquals("large egg", saved.title)
        assertEquals(150.0, saved.totals.caloriesKcal!!, 0.0)
        assertEquals(10.0, saved.totals.fatG!!, 0.0)
        assertEquals(100.0, saved.totals.amountGml!!, 0.0)
        assertEquals(12.5, saved.totals.proteinG!!, 0.0)
        assertEquals(3.2, saved.totals.saturatedFatG!!, 0.0)
        assertEquals(0.4, saved.totals.sugarG!!, 0.0)
        assertEquals(0.3, saved.totals.saltG!!, 0.0)
        assertEquals("https://fdc.nal.usda.gov/fdc-app.html#/food-details/748967/nutrients", saved.source)
        assertEquals(ConfidenceLevel.MEDIUM, saved.confidence)
        assertEquals("large egg", saved.items.single().name)
    }

    @Test
    fun editableDraftParsesFloatingMacroValues() {
        val draft = sampleResult().toEditableDraft().copy(
            items = listOf(
                sampleResult().toEditableDraft().items.single().copy(
                    caloriesKcal = "150.5",
                    amountGml = "100,5",
                    proteinG = "12.75",
                    carbsG = "1,25",
                    fatG = "10.5",
                    saturatedFatG = "3,25",
                    sugarG = "0.45",
                    saltG = "0",
                ),
            ),
        )

        val saved = draft.toEntity(createdAtEpochMillis = 123L).toSavedMeal()

        assertEquals(150.5, saved.totals.caloriesKcal!!, 0.0)
        assertEquals(100.5, saved.totals.amountGml!!, 0.0)
        assertEquals(12.75, saved.totals.proteinG!!, 0.0)
        assertEquals(1.25, saved.totals.carbsG!!, 0.0)
        assertEquals(10.5, saved.totals.fatG!!, 0.0)
        assertEquals(3.25, saved.totals.saturatedFatG!!, 0.0)
        assertEquals(0.45, saved.totals.sugarG!!, 0.0)
        assertEquals(0.0, saved.totals.saltG!!, 0.0)
    }

    @Test
    fun savedMealBecomesEditableDraftAndCanPreserveIdForUpdate() {
        val saved = sampleResult().toEditableDraft().toEntity(createdAtEpochMillis = 123L).copy(id = 42L).toSavedMeal()
        val draft = saved.toEditableDraft().copy(query = "updated eggs", createdAtEpochMillis = 456L)
        val updated = draft.toEntity(id = saved.id, createdAtEpochMillis = draft.createdAtEpochMillis ?: saved.createdAtEpochMillis)

        assertEquals(42L, updated.id)
        assertEquals(456L, updated.createdAtEpochMillis)
        assertEquals("large egg", updated.title)
        assertEquals("updated eggs", updated.query)
        assertEquals("https://fdc.nal.usda.gov/fdc-app.html#/food-details/748967/nutrients", updated.source)
    }

    @Test
    fun customMealTitleIsPreservedWhenSaving() {
        val draft = sampleResult().toEditableDraft().copy(title = "Breakfast eggs")

        val saved = draft.toEntity(createdAtEpochMillis = 123L).toSavedMeal()

        assertEquals("Breakfast eggs", saved.title)
        assertEquals("2 eggs", saved.query)
    }

    @Test
    fun sourceFieldsTrimAndBlankNonHttpUrls() {
        val result = sampleResult().copy(
            source = " USDA FoodData Central ",
            items = listOf(sampleResult().items.single().copy(source = " https://example.com/item ")),
        )
        val draft = result.toEditableDraft()

        assertEquals("https://example.com/item", draft.source)
        assertEquals("https://example.com/item", draft.items.single().source)

        val saved = draft.copy(source = " USDA FoodData Central ").toEntity(createdAtEpochMillis = 123L).toSavedMeal()

        assertEquals("", saved.source)
        assertEquals("https://example.com/item", saved.items.single().source)
    }

    @Test
    fun invalidNewNutritionNumbersBecomeNullButZeroIsPreserved() {
        val draft = sampleResult().toEditableDraft().copy(
            items = listOf(
                sampleResult().toEditableDraft().items.single().copy(
                    amountGml = "bad",
                    saturatedFatG = "bad",
                    sugarG = "",
                    saltG = "0",
                ),
            ),
        )

        val saved = draft.toEntity(createdAtEpochMillis = 123L).toSavedMeal()

        assertEquals(0.0, saved.totals.saturatedFatG!!, 0.0)
        assertEquals(0.0, saved.totals.amountGml!!, 0.0)
        assertEquals(0.0, saved.totals.sugarG!!, 0.0)
        assertEquals(0.0, saved.totals.saltG!!, 0.0)
    }

    @Test
    fun staleEditableTotalsAreRecomputedFromItemsOnSave() {
        val draft = sampleResult().toEditableDraft().copy(
            caloriesKcal = "999",
            amountGml = "999",
            proteinG = "999",
            carbsG = "999",
            fatG = "999",
            saturatedFatG = "999",
            sugarG = "999",
            saltG = "999",
        )

        val saved = draft.toEntity(createdAtEpochMillis = 123L).toSavedMeal()

        assertEquals(150.0, saved.totals.caloriesKcal!!, 0.0)
        assertEquals(100.0, saved.totals.amountGml!!, 0.0)
        assertEquals(12.5, saved.totals.proteinG!!, 0.0)
        assertEquals(1.0, saved.totals.carbsG!!, 0.0)
        assertEquals(10.0, saved.totals.fatG!!, 0.0)
        assertEquals(3.2, saved.totals.saturatedFatG!!, 0.0)
        assertEquals(0.4, saved.totals.sugarG!!, 0.0)
        assertEquals(0.3, saved.totals.saltG!!, 0.0)
    }

    @Test
    fun oldItemJsonWithoutAmountDefaultsToNull() {
        val entity = sampleResult().toEditableDraft().toEntity(createdAtEpochMillis = 123L).copy(
            itemsJson = """
                [{"name":"legacy item","quantity":"1 serving","caloriesKcal":42.0,"proteinG":1.0,"carbsG":2.0,"fatG":3.0,"reasoning":"old"}]
            """.trimIndent(),
        )

        val saved = entity.toSavedMeal()

        assertNull(saved.items.single().amountGml)
    }

    @Test
    fun findCachedMealMatchPrefersFirstMatchingMealAndScalesRequestedAmount() {
        val meals = listOf(
            savedMeal(
                id = 1L,
                query = "100g cucumber",
                title = "Cucumber salad",
                amountGml = 100.0,
                caloriesKcal = 20.0,
                proteinG = 1.0,
                carbsG = 2.0,
                fatG = 3.0,
            ),
            savedMeal(
                id = 2L,
                query = "100g cucumber",
                title = "Cucumber salad copy",
                amountGml = 100.0,
                caloriesKcal = 99.0,
                proteinG = 9.0,
                carbsG = 9.0,
                fatG = 9.0,
            ),
        )

        val match = findCachedMealMatch(meals, "200g cucumber")

        assertNotNull(match)
        assertEquals(1L, match!!.meal.id)
        assertEquals("200g cucumber", match.draft.query)
        assertEquals("200", match.draft.amountGml)
        assertEquals("40", match.draft.caloriesKcal)
        assertEquals("2", match.draft.proteinG)
    }

    @Test
    fun searchSavedMealsMatchesTitleQueryAndItemNamesInOrderWithLimit() {
        val meals = listOf(
            savedMeal(
                id = 1L,
                query = "snack",
                title = "Egg salad",
                itemName = "lettuce",
            ),
            savedMeal(
                id = 2L,
                query = "2 eggs",
                title = "Breakfast",
                itemName = "toast",
            ),
            savedMeal(
                id = 3L,
                query = "omelet",
                title = "Dinner",
                itemName = "fried egg",
            ),
            savedMeal(
                id = 4L,
                query = "lunch",
                title = "Soup",
                itemName = "carrot",
            ),
        )

        val matches = searchSavedMeals(meals, "egg", limit = 2)

        assertEquals(listOf(1L, 2L), matches.map { it.id })
    }

    @Test
    fun searchSavedMealsIgnoresAmountTokensInQueryMatching() {
        val meals = listOf(
            savedMeal(
                id = 1L,
                query = "cucumber",
                title = "Cucumber bowl",
                itemName = "cucumber",
            ),
            savedMeal(
                id = 2L,
                query = "tomato",
                title = "Tomato bowl",
                itemName = "tomato",
            ),
        )

        val matches = searchSavedMeals(meals, "200g cucumber")

        assertEquals(listOf(1L), matches.map { it.id })
    }

    private fun sampleResult() = NutritionAgentResult(
        query = "2 eggs",
        items = listOf(
            FoodItemEstimate(
                name = "large egg",
                quantity = "2 eggs",
                amountGml = 100.0,
                caloriesKcal = 150.0,
                proteinG = 12.5,
                carbsG = 1.0,
                fatG = 10.0,
                saturatedFatG = 3.2,
                sugarG = 0.4,
                saltG = 0.3,
                source = "https://fdc.nal.usda.gov/fdc-app.html#/food-details/748967/nutrients",
                reasoning = "Typical estimate.",
            ),
        ),
        totals = NutritionTotals(
            caloriesKcal = 150.0,
            amountGml = 100.0,
            proteinG = 12.5,
            carbsG = 1.0,
            fatG = 10.0,
            saturatedFatG = 3.2,
            sugarG = 0.4,
            saltG = 0.3,
        ),
        source = "https://fdc.nal.usda.gov/fdc-app.html#/food-details/748967/nutrients",
        assumptions = listOf("assumption one", "assumption two"),
        warnings = listOf("review before saving"),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = true,
    )

    private fun savedMeal(
        id: Long,
        query: String,
        title: String,
        amountGml: Double = 100.0,
        caloriesKcal: Double = 10.0,
        proteinG: Double = 1.0,
        carbsG: Double = 1.0,
        fatG: Double = 1.0,
        itemName: String = title,
    ) = com.example.vocalorie.model.SavedMeal(
        id = id,
        createdAtEpochMillis = 123L,
        title = title,
        query = query,
        items = listOf(
            FoodItemEstimate(
                name = itemName,
                quantity = query,
                amountGml = amountGml,
                caloriesKcal = caloriesKcal,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                saturatedFatG = null,
                sugarG = null,
                saltG = null,
                source = "",
                reasoning = "",
            ),
        ),
        totals = NutritionTotals(
            caloriesKcal = caloriesKcal,
            amountGml = amountGml,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            saturatedFatG = null,
            sugarG = null,
            saltG = null,
        ),
        source = "",
        assumptions = emptyList(),
        warnings = emptyList(),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = false,
    )
}
