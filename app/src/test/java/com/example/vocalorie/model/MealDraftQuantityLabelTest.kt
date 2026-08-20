package com.example.vocalorie.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class MealDraftQuantityLabelTest {
    @Test
    fun scalesTheLeadingNumberAndKeepsTheWords() {
        assertEquals("4 eggs", scaledLabel("2 eggs", factor = "2"))
        assertEquals("750 ml", scaledLabel("500 ml", factor = "1.5"))
        assertEquals("1.5 Scheibe", scaledLabel("1 Scheibe", factor = "1.5"))
        assertEquals("3 l", scaledLabel("1,5 l", factor = "2"))
    }

    @Test
    fun roundsTheScaledNumberToOneDecimal() {
        assertEquals("0.7 Scheibe", scaledLabel("1 Scheibe", factor = "0.748"))
    }

    @Test
    fun derivesTheLabelFromTheScaledAmountWhenThereIsNoLeadingNumber() {
        assertEquals("200 g", scaledLabel("eine Handvoll", amountGml = "100", factor = "2"))
        assertEquals("300 ml", scaledLabel("einige ml", amountGml = "300", factor = "1"))
        assertEquals("300 g", scaledLabel("ein Schluck Milch", amountGml = "300", factor = "1"))
        assertEquals("100 g", scaledLabel("", amountGml = "100", factor = "1"))
    }

    @Test
    fun leavesTheLabelAloneWhenThereIsNothingToDeriveFrom() {
        assertEquals("eine Handvoll", scaledLabel("eine Handvoll", amountGml = "", factor = "2"))
        assertEquals("eine Handvoll", scaledLabel("eine Handvoll", amountGml = "0", factor = "2"))
        assertEquals("", scaledLabel("", amountGml = "", factor = "2"))
    }

    @Test
    fun quantityMovesAlongsideTheAmountForEveryItem() {
        val draft = sampleDraft().copy(
            items = listOf(
                sampleItem().copy(quantity = "2 eggs", amountGml = "120"),
                sampleItem().copy(quantity = "500 ml", amountGml = "500"),
            ),
        )

        val scaled = draft.withItemsScaledByFactor(BigDecimal("0.5"))

        assertEquals(listOf("1 eggs", "250 ml"), scaled.items.map { it.quantity })
        assertEquals(listOf("60", "250"), scaled.items.map { it.amountGml })
    }

    @Test
    fun portionScalingFromBaselineScalesTheLabel() {
        val baseline = listOf(sampleItem().copy(quantity = "400 g", amountGml = "400"))
        val draft = sampleDraft().copy(items = baseline)

        val scaled = draft.withItemsScaledByPortionFromBaseline(
            recipeMakes = "4",
            ate = "1",
            baselineItems = baseline,
        )

        assertEquals("100 g", scaled!!.items.single().quantity)
        assertEquals("100", scaled.items.single().amountGml)
    }

    @Test
    fun anUnparseableLabelBecomesConsistentWithItsScaledAmount() {
        val draft = sampleDraft().copy(
            items = listOf(sampleItem().copy(quantity = "eine Handvoll", amountGml = "100")),
        )

        val item = draft.withItemsScaledByFactor(BigDecimal("2")).items.single()

        assertEquals("200 g", item.quantity)
        assertEquals("200", item.amountGml)
    }

    @Test
    fun scalingLeavesTheItemNameAlone() {
        val draft = sampleDraft().copy(
            items = listOf(sampleItem().copy(name = "2 Eier", quantity = "2 eggs", amountGml = "120")),
        )

        assertEquals("2 Eier", draft.withItemsScaledByFactor(BigDecimal("2")).items.single().name)
    }

    private fun scaledLabel(label: String, factor: String, amountGml: String = "0"): String {
        val draft = sampleDraft().copy(
            items = listOf(sampleItem().copy(quantity = label, amountGml = amountGml)),
        )
        return draft.withItemsScaledByFactor(BigDecimal(factor)).items.single().quantity
    }

    private fun sampleDraft() = EditableMealDraft(
        title = "meal",
        query = "meal",
        items = emptyList(),
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
    )

    private fun sampleItem() = EditableFoodItem(
        name = "item",
        quantity = "",
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
