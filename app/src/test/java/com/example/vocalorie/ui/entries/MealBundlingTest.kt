package com.example.vocalorie.ui.entries

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.model.SavedMeal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MealBundlingTest {
    private fun meal(
        id: Long,
        createdAt: Instant,
        calories: Double? = 100.0,
        protein: Double? = 10.0,
        carbs: Double? = 20.0,
        fat: Double? = 5.0,
        amount: Double? = 150.0,
        saturatedFat: Double? = 1.0,
        sugar: Double? = 2.0,
        salt: Double? = 0.1,
    ) = SavedMeal(
        id = id,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        query = "meal $id",
        items = emptyList(),
        totals = NutritionTotals(
            caloriesKcal = calories,
            amountGml = amount,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            saturatedFatG = saturatedFat,
            sugarG = sugar,
            saltG = salt,
        ),
        assumptions = emptyList(),
        warnings = emptyList(),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = false,
    )

    @Test
    fun emptyInputProducesEmptyBundleList() {
        assertEquals(emptyList<MealBundle>(), bundleMealsByTime(emptyList()))
    }

    @Test
    fun singleMealProducesSingleEntryBundle() {
        val m = meal(1, Instant.ofEpochMilli(0))
        val bundles = bundleMealsByTime(listOf(m))
        assertEquals(1, bundles.size)
        val bundle = bundles.first()
        assertEquals("bundle_1_1", bundle.id)
        assertEquals(1, bundle.meals.size)
        assertEquals(m.totals.caloriesKcal ?: 0.0, bundle.totals.caloriesKcal ?: 0.0, 0.0)
        assertEquals(m.totals.proteinG ?: 0.0, bundle.totals.proteinG ?: 0.0, 0.0)
        assertEquals(m.totals.carbsG ?: 0.0, bundle.totals.carbsG ?: 0.0, 0.0)
        assertEquals(m.totals.fatG ?: 0.0, bundle.totals.fatG ?: 0.0, 0.0)
        assertEquals(m.createdAtEpochMillis, bundle.startTimeEpochMillis)
        assertEquals(m.createdAtEpochMillis, bundle.endTimeEpochMillis)
        assertEquals(false, bundle.isMultiEntry)
    }

    @Test
    fun twoMealsWith10MinuteGapGroupIntoOneBundle() {
        val base = Instant.ofEpochMilli(1_000_000)
        val m1 = meal(1, base)
        val m2 = meal(2, base.plusMillis(10 * 60 * 1000))
        val bundles = bundleMealsByTime(listOf(m1, m2))
        assertEquals(1, bundles.size)
        assertEquals("bundle_1_2", bundles.first().id)
        assertEquals(2, bundles.first().meals.size)
        assertEquals(true, bundles.first().isMultiEntry)
    }

    @Test
    fun twoMealsWith16MinuteGapSplitIntoSingleEntryBundles() {
        val base = Instant.ofEpochMilli(1_000_000)
        val m1 = meal(1, base)
        val m2 = meal(2, base.plusMillis(16 * 60 * 1000))
        val bundles = bundleMealsByTime(listOf(m1, m2))
        assertEquals(2, bundles.size)
        assertEquals("bundle_1_1", bundles[0].id)
        assertEquals("bundle_2_2", bundles[1].id)
        assertEquals(false, bundles[0].isMultiEntry)
        assertEquals(false, bundles[1].isMultiEntry)
    }

    @Test
    fun exactly15MinuteGapGroupsIntoOneBundle() {
        val base = Instant.ofEpochMilli(1_000_000)
        val m1 = meal(1, base)
        val m2 = meal(2, base.plusMillis(15 * 60 * 1000))
        val bundles = bundleMealsByTime(listOf(m1, m2))
        assertEquals(1, bundles.size)
        assertEquals(2, bundles.first().meals.size)
    }

    @Test
    fun chainedMealsGroupIntoSingleBundle() {
        val base = Instant.ofEpochMilli(1_000_000)
        val m1 = meal(1, base)
        val m2 = meal(2, base.plusMillis(12 * 60 * 1000))
        val m3 = meal(3, base.plusMillis(24 * 60 * 1000))
        val bundles = bundleMealsByTime(listOf(m1, m2, m3))
        assertEquals(1, bundles.size)
        assertEquals(3, bundles.first().meals.size)
        assertEquals("bundle_1_3", bundles.first().id)
    }

    @Test
    fun multiBundleSequenceFormsSeparateBundles() {
        val base = Instant.ofEpochMilli(1_000_000)
        val breakfast1 = meal(1, base)
        val breakfast2 = meal(2, base.plusMillis(10 * 60 * 1000))
        val lunch1 = meal(3, base.plusMillis(4 * 60 * 60 * 1000))
        val lunch2 = meal(4, base.plusMillis(4 * 60 * 60 * 1000 + 10 * 60 * 1000))
        val bundles = bundleMealsByTime(listOf(breakfast1, breakfast2, lunch1, lunch2))
        assertEquals(2, bundles.size)
        assertEquals("bundle_1_2", bundles[0].id)
        assertEquals(2, bundles[0].meals.size)
        assertEquals("bundle_3_4", bundles[1].id)
        assertEquals(2, bundles[1].meals.size)
    }

    @Test
    fun aggregateTotalsEqualSumOfConstituentMeals() {
        val base = Instant.ofEpochMilli(1_000_000)
        val m1 = meal(1, base, calories = 200.0, protein = 20.0, carbs = 40.0, fat = 10.0, amount = 300.0)
        val m2 = meal(2, base.plusMillis(5 * 60 * 1000), calories = 300.0, protein = 30.0, carbs = 60.0, fat = 15.0, amount = 450.0)
        val bundles = bundleMealsByTime(listOf(m1, m2))
        assertEquals(1, bundles.size)
        val totals = bundles.first().totals
        assertEquals(500.0, totals.caloriesKcal ?: 0.0, 0.0)
        assertEquals(50.0, totals.proteinG ?: 0.0, 0.0)
        assertEquals(100.0, totals.carbsG ?: 0.0, 0.0)
        assertEquals(25.0, totals.fatG ?: 0.0, 0.0)
        assertEquals(750.0, totals.amountGml ?: 0.0, 0.0)
    }

    @Test
    fun nullTotalsAreTreatedAsZeroInAggregation() {
        val base = Instant.ofEpochMilli(1_000_000)
        val m1 = meal(1, base, calories = null, protein = null, carbs = null, fat = null, amount = null)
        val m2 = meal(2, base.plusMillis(5 * 60 * 1000), calories = 100.0, protein = 10.0, carbs = 20.0, fat = 5.0, amount = 150.0)
        val bundles = bundleMealsByTime(listOf(m1, m2))
        assertEquals(1, bundles.size)
        val totals = bundles.first().totals
        assertEquals(100.0, totals.caloriesKcal ?: 0.0, 0.0)
        assertEquals(10.0, totals.proteinG ?: 0.0, 0.0)
        assertEquals(20.0, totals.carbsG ?: 0.0, 0.0)
        assertEquals(5.0, totals.fatG ?: 0.0, 0.0)
        assertEquals(150.0, totals.amountGml ?: 0.0, 0.0)
    }

    @Test
    fun bundleTimeBoundsReflectFirstAndLastMeal() {
        val base = Instant.ofEpochMilli(1_000_000)
        val m1 = meal(1, base)
        val m2 = meal(2, base.plusMillis(10 * 60 * 1000))
        val m3 = meal(3, base.plusMillis(20 * 60 * 1000))
        val bundles = bundleMealsByTime(listOf(m1, m2, m3))
        assertEquals(1, bundles.size)
        val bundle = bundles.first()
        assertEquals(m1.createdAtEpochMillis, bundle.startTimeEpochMillis)
        assertEquals(m3.createdAtEpochMillis, bundle.endTimeEpochMillis)
    }
}
