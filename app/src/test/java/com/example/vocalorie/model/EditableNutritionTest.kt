package com.example.vocalorie.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every field carries a distinct value so transposing any two mappings in
 * [toEditableNutrition] or [withNutrition] fails these round trips.
 */
class EditableNutritionTest {
    @Test
    fun readsAllEightItemNutritionFieldsIntoTheirOwnSlot() {
        val item = sampleItem().copy(
            caloriesKcal = CALORIES,
            amountGml = AMOUNT,
            fatG = FAT,
            saturatedFatG = SATURATED_FAT,
            carbsG = CARBS,
            sugarG = SUGAR,
            proteinG = PROTEIN,
            saltG = SALT,
        )

        assertEquals(distinctNutrition(), item.toEditableNutrition())
    }

    @Test
    fun writesAllEightItemNutritionFieldsBackWithoutTouchingOtherFields() {
        val item = sampleItem()

        val updated = item.withNutrition(distinctNutrition())

        assertEquals(CALORIES, updated.caloriesKcal)
        assertEquals(AMOUNT, updated.amountGml)
        assertEquals(FAT, updated.fatG)
        assertEquals(SATURATED_FAT, updated.saturatedFatG)
        assertEquals(CARBS, updated.carbsG)
        assertEquals(SUGAR, updated.sugarG)
        assertEquals(PROTEIN, updated.proteinG)
        assertEquals(SALT, updated.saltG)
        assertEquals(item.name, updated.name)
        assertEquals(item.quantity, updated.quantity)
        assertEquals(item.source, updated.source)
        assertEquals(item.reasoning, updated.reasoning)
    }

    @Test
    fun itemNutritionRoundTripsInBothDirections() {
        val item = sampleItem().withNutrition(distinctNutrition())

        assertEquals(distinctNutrition(), item.toEditableNutrition())
        assertEquals(item, item.withNutrition(item.toEditableNutrition()))
    }

    @Test
    fun readsAllEightDraftNutritionFieldsIntoTheirOwnSlot() {
        val draft = sampleDraft().copy(
            caloriesKcal = CALORIES,
            amountGml = AMOUNT,
            fatG = FAT,
            saturatedFatG = SATURATED_FAT,
            carbsG = CARBS,
            sugarG = SUGAR,
            proteinG = PROTEIN,
            saltG = SALT,
        )

        assertEquals(distinctNutrition(), draft.toEditableNutrition())
    }

    @Test
    fun writesAllEightDraftNutritionFieldsBackWithoutTouchingOtherFields() {
        val draft = sampleDraft()

        val updated = draft.withNutrition(distinctNutrition())

        assertEquals(CALORIES, updated.caloriesKcal)
        assertEquals(AMOUNT, updated.amountGml)
        assertEquals(FAT, updated.fatG)
        assertEquals(SATURATED_FAT, updated.saturatedFatG)
        assertEquals(CARBS, updated.carbsG)
        assertEquals(SUGAR, updated.sugarG)
        assertEquals(PROTEIN, updated.proteinG)
        assertEquals(SALT, updated.saltG)
        assertEquals(draft.title, updated.title)
        assertEquals(draft.query, updated.query)
        assertEquals(draft.items, updated.items)
        assertEquals(draft.assumptionsText, updated.assumptionsText)
        assertEquals(draft.warningsText, updated.warningsText)
        assertEquals(draft.confidence, updated.confidence)
        assertEquals(draft.needsHumanReview, updated.needsHumanReview)
        assertEquals(draft.createdAtEpochMillis, updated.createdAtEpochMillis)
        assertEquals(draft.category, updated.category)
    }

    @Test
    fun draftNutritionRoundTripsInBothDirections() {
        val draft = sampleDraft().withNutrition(distinctNutrition())

        assertEquals(distinctNutrition(), draft.toEditableNutrition())
        assertEquals(draft, draft.withNutrition(draft.toEditableNutrition()))
    }

    @Test
    fun editingOneNutritionFieldLeavesTheOtherSevenUntouched() {
        val edited = distinctNutrition().copy(carbs = "999")

        assertEquals(CALORIES, edited.calories)
        assertEquals(AMOUNT, edited.amount)
        assertEquals(FAT, edited.fat)
        assertEquals(SATURATED_FAT, edited.saturatedFat)
        assertEquals("999", edited.carbs)
        assertEquals(SUGAR, edited.sugar)
        assertEquals(PROTEIN, edited.protein)
        assertEquals(SALT, edited.salt)
    }

    @Test
    fun editingOneItemNutritionFieldWritesOnlyThatField() {
        val item = sampleItem().withNutrition(distinctNutrition())

        val updated = item.withNutrition(item.toEditableNutrition().copy(sugar = "999"))

        assertEquals("999", updated.sugarG)
        assertEquals(item.copy(sugarG = "999"), updated)
    }

    private fun distinctNutrition(): EditableNutrition = EditableNutrition(
        calories = CALORIES,
        amount = AMOUNT,
        fat = FAT,
        saturatedFat = SATURATED_FAT,
        carbs = CARBS,
        sugar = SUGAR,
        protein = PROTEIN,
        salt = SALT,
    )

    private fun sampleItem(): EditableFoodItem = EditableFoodItem(
        name = "Oat porridge",
        quantity = "1 bowl",
        amountGml = "0",
        caloriesKcal = "0",
        proteinG = "0",
        carbsG = "0",
        fatG = "0",
        saturatedFatG = "0",
        sugarG = "0",
        saltG = "0",
        source = "https://example.com/oats",
        reasoning = "Standard bowl",
    )

    private fun sampleDraft(): EditableMealDraft = EditableMealDraft(
        title = "Breakfast",
        query = "oat porridge",
        items = listOf(sampleItem()),
        caloriesKcal = "0",
        amountGml = "0",
        proteinG = "0",
        carbsG = "0",
        fatG = "0",
        saturatedFatG = "0",
        sugarG = "0",
        saltG = "0",
        assumptionsText = "assumed a standard bowl",
        warningsText = "no warnings",
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = false,
        createdAtEpochMillis = 1_700_000_000_000L,
        category = MealCategory.MEAL,
    )

    private companion object {
        const val CALORIES = "111"
        const val AMOUNT = "222"
        const val FAT = "333"
        const val SATURATED_FAT = "444"
        const val CARBS = "555"
        const val SUGAR = "666"
        const val PROTEIN = "777"
        const val SALT = "888"
    }
}
