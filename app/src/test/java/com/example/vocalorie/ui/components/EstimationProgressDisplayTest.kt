package com.example.vocalorie.ui.components

import com.example.vocalorie.ai.EstimationProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimationProgressDisplayTest {

    @Test
    fun preparingRendersItsWording() {
        assertEquals("Preparing the estimate…", EstimationProgress.Preparing.displayText())
    }

    @Test
    fun searchingSourcesRendersItsWording() {
        assertEquals("Looking for sources…", EstimationProgress.SearchingSources.displayText())
    }

    @Test
    fun readingSourceRendersTheHostOnly() {
        val text = EstimationProgress.ReadingSource("https://fddb.info/db/de/lebensmittel/apfel/?q=x&y=1").displayText()
        assertEquals("Reading fddb.info…", text)
    }

    @Test
    fun calculatingNutritionRendersItsWording() {
        assertEquals("Computing nutrition values…", EstimationProgress.CalculatingNutrition.displayText())
    }

    @Test
    fun noCaseRendersARawToolIdentifier() {
        val allTexts = listOf(
            EstimationProgress.Preparing,
            EstimationProgress.SearchingSources,
            EstimationProgress.ReadingSource("https://fddb.info/db/de/lebensmittel/apfel/"),
            EstimationProgress.CalculatingNutrition,
        ).map { it.displayText() }

        allTexts.forEach { text ->
            assertFalse(text.contains("web_fetch"))
            assertFalse(text.contains("brave_search"))
        }
        assertTrue(allTexts.all { it.isNotBlank() })
    }
}
