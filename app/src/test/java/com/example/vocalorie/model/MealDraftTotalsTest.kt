package com.example.vocalorie.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealDraftTotalsTest {
    @Test
    fun sumsEditableItemNutritionIntoMealTotals() {
        val draft = sampleDraft().copy(
            source = "https://example.com/meal",
            items = listOf(
                sampleItem().copy(
                    amountGml = "100.5",
                    caloriesKcal = "150",
                    proteinG = "12.25",
                    carbsG = "1,5",
                    fatG = "10.1",
                    saturatedFatG = "3.20",
                    sugarG = "0.45",
                    saltG = "0.30",
                    source = "https://example.com/item-one",
                ),
                sampleItem().copy(
                    amountGml = "bad",
                    caloriesKcal = "25.5",
                    proteinG = "",
                    carbsG = "2.25",
                    fatG = "0.2",
                    saturatedFatG = "0.05",
                    sugarG = "1.55",
                    saltG = "0",
                    source = "https://example.com/item-two",
                ),
            ),
        )

        val updated = draft.withTotalsSummedFromItems()

        assertEquals("100.5", updated.amountGml)
        assertEquals("175.5", updated.caloriesKcal)
        assertEquals("12.25", updated.proteinG)
        assertEquals("3.75", updated.carbsG)
        assertEquals("10.3", updated.fatG)
        assertEquals("3.25", updated.saturatedFatG)
        assertEquals("2", updated.sugarG)
        assertEquals("0.3", updated.saltG)
        assertEquals("https://example.com/meal", updated.source)
    }

    @Test
    fun scalesEditableItemsByRecipePortionAndRecomputesTotals() {
        val draft = sampleDraft().copy(
            items = listOf(
                sampleItem().copy(
                    quantity = "whole recipe amount",
                    amountGml = "400",
                    caloriesKcal = "800",
                    proteinG = "40",
                    carbsG = "120",
                    fatG = "20",
                    saturatedFatG = "8",
                    sugarG = "16",
                    saltG = "4",
                    source = "https://example.com/item",
                ),
                sampleItem().copy(
                    amountGml = "bad",
                    caloriesKcal = "100",
                    proteinG = "",
                    carbsG = "10",
                    fatG = "5",
                    saturatedFatG = "1",
                    sugarG = "2",
                    saltG = "0.5",
                ),
            ),
        )

        val scaled = draft.withItemsScaledByPortion(recipeMakes = "4", ate = "1")

        assertEquals("whole recipe amount", scaled!!.items.first().quantity)
        assertEquals("https://example.com/item", scaled.items.first().source)
        assertEquals("100", scaled.items.first().amountGml)
        assertEquals("200", scaled.items.first().caloriesKcal)
        assertEquals("10", scaled.items.first().proteinG)
        assertEquals("30", scaled.items.first().carbsG)
        assertEquals("5", scaled.items.first().fatG)
        assertEquals("2", scaled.items.first().saturatedFatG)
        assertEquals("4", scaled.items.first().sugarG)
        assertEquals("1", scaled.items.first().saltG)
        assertEquals("bad", scaled.items[1].amountGml)
        assertEquals("225", scaled.caloriesKcal)
        assertEquals("32.5", scaled.carbsG)
        assertEquals("1.125", scaled.saltG)
    }

    @Test
    fun scalesPortionFromBaselineSoRepeatedApplyIsNotCumulative() {
        val draft = sampleDraft().copy(
            items = listOf(
                sampleItem().copy(
                    amountGml = "400",
                    caloriesKcal = "800",
                    proteinG = "40",
                    carbsG = "120",
                    fatG = "20",
                    saturatedFatG = "8",
                    sugarG = "16",
                    saltG = "4",
                ),
            ),
        )
        val baselineItems = draft.items

        val firstApply = draft.withItemsScaledByPortionFromBaseline(
            recipeMakes = "4",
            ate = "1",
            baselineItems = baselineItems,
        )!!
        val repeatedApply = firstApply.withItemsScaledByPortionFromBaseline(
            recipeMakes = "4",
            ate = "1",
            baselineItems = baselineItems,
        )!!

        assertEquals("100", firstApply.items.first().amountGml)
        assertEquals("200", firstApply.items.first().caloriesKcal)
        assertEquals(firstApply.items, repeatedApply.items)
        assertEquals(firstApply.caloriesKcal, repeatedApply.caloriesKcal)
    }

    @Test
    fun changingPortionUsesBaselineInsteadOfScalingAlreadyScaledItems() {
        val draft = sampleDraft().copy(
            items = listOf(
                sampleItem().copy(
                    caloriesKcal = "800",
                    carbsG = "120",
                ),
            ),
        )
        val baselineItems = draft.items

        val quarter = draft.withItemsScaledByPortionFromBaseline(
            recipeMakes = "4",
            ate = "1",
            baselineItems = baselineItems,
        )!!
        val half = quarter.withItemsScaledByPortionFromBaseline(
            recipeMakes = "2",
            ate = "1",
            baselineItems = baselineItems,
        )!!

        assertEquals("200", quarter.items.first().caloriesKcal)
        assertEquals("400", half.items.first().caloriesKcal)
        assertEquals("60", half.items.first().carbsG)
    }

    @Test
    fun invalidPortionValuesDoNotScaleDraft() {
        assertNull(sampleDraft().withItemsScaledByPortion(recipeMakes = "0", ate = "1"))
        assertNull(sampleDraft().withItemsScaledByPortion(recipeMakes = "4", ate = ""))
        assertNull(sampleDraft().withItemsScaledByPortion(recipeMakes = "bad", ate = "1"))
    }

    private fun sampleDraft() = EditableMealDraft(
        title = "meal",
        query = "meal",
        items = emptyList(),
        caloriesKcal = "999",
        amountGml = "999",
        proteinG = "999",
        carbsG = "999",
        fatG = "999",
        saturatedFatG = "999",
        sugarG = "999",
        saltG = "999",
        source = "",
        assumptionsText = "",
        warningsText = "",
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = false,
    )

    private fun sampleItem() = EditableFoodItem(
        name = "item",
        quantity = "1 serving",
        amountGml = "",
        caloriesKcal = "",
        proteinG = "",
        carbsG = "",
        fatG = "",
        saturatedFatG = "",
        sugarG = "",
        saltG = "",
        source = "",
        reasoning = "",
    )
}
