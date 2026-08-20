package com.example.vocalorie.ui.entries.stats

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatmapLabelFormatTest {

    private val may18 = LocalDate.of(2026, 5, 18)
    private val august20 = LocalDate.of(2026, 8, 20)

    @Test
    fun `both range ends share one shape with no trailing abbreviation period`() {
        val first = heatmapRangeLabel(may18, Locale.GERMAN)
        val last = heatmapRangeLabel(august20, Locale.GERMAN)

        assertEquals("18.05.", first)
        assertEquals("20.08.", last)
        assertEquals(first.length, last.length)
        // The observed defect was one end carrying an abbreviation period the other lacked; a
        // numeric pattern ends both ends with the same separator instead.
        assertEquals(first.last(), last.last())
    }

    @Test
    fun `the same dates render identically across locales`() {
        val locales = listOf(Locale.GERMAN, Locale.US, Locale.FRENCH)

        val firsts = locales.map { heatmapRangeLabel(may18, it) }.distinct()
        val lasts = locales.map { heatmapRangeLabel(august20, it) }.distinct()

        assertEquals(listOf("18.05."), firsts)
        assertEquals(listOf("20.08."), lasts)
    }

    @Test
    fun `no label contains a letter in any locale`() {
        listOf(Locale.GERMAN, Locale.US, Locale.FRENCH).forEach { locale ->
            listOf(may18, august20).forEach { date ->
                val label = heatmapRangeLabel(date, locale)
                assertFalse("$label in $locale contains a letter", label.any { it.isLetter() })
                assertTrue(label.any { it.isDigit() })
            }
        }
    }
}
