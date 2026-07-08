package com.example.vocalorie.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealCalorieStyleTest {
    @Test
    fun calorieBucketsUseExpectedBoundaries() {
        assertEquals(MealCalorieBucket.NEUTRAL, mealCalorieBucket(null))
        assertEquals(MealCalorieBucket.NEUTRAL, mealCalorieBucket(Double.NaN))
        assertEquals(MealCalorieBucket.CREAM_YELLOW, mealCalorieBucket(0.0))
        assertEquals(MealCalorieBucket.CREAM_YELLOW, mealCalorieBucket(149.999))

        assertEquals(MealCalorieBucket.SOFT_YELLOW, mealCalorieBucket(150.0))
        assertEquals(MealCalorieBucket.SOFT_YELLOW, mealCalorieBucket(399.999))

        assertEquals(MealCalorieBucket.ORANGE, mealCalorieBucket(400.0))
        assertEquals(MealCalorieBucket.ORANGE, mealCalorieBucket(799.999))

        assertEquals(MealCalorieBucket.DEEP_ORANGE, mealCalorieBucket(800.0))
    }

    @Test
    fun savedEntryBackgroundStyleDoesNotUseConfidenceOrReviewState() {
        val mealEntriesScreen = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt")

        assertTrue(mealEntriesScreen.contains("mealCalorieStateStyle(meal.totals.caloriesKcal)"))
        assertTrue(mealEntriesScreen.contains("formatDate(meal.createdAtEpochMillis)"))
        assertTrue(mealEntriesScreen.contains("color = style.contentColor"))
        assertFalse(mealEntriesScreen.contains("mealStateStyle(meal)"))
        assertFalse(mealEntriesScreen.contains("if (meal.needsHumanReview) 2.dp else 1.dp"))
    }

    private fun source(path: String): String = File(path).takeIf { it.exists() }
        ?.readText()
        ?: File("../$path").readText()
}
