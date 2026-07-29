package com.example.vocalorie.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the one formatter every editable numeric field goes through. The four spurious-precision
 * values below were read off a real device before this formatter existed, where a `Double.toString()`
 * put the raw binary artefact straight into the text field.
 */
class EditableNumberTextTest {
    @Test
    fun roundsAwaySpuriousPrecisionObservedInTheMealEditorTotals() {
        assertEquals("816.65", 816.6500000000000004.toEditableNumberText())
        assertEquals("5.7", 5.7000000000000004.toEditableNumberText())
        assertEquals("3.45", 3.450000000000004.toEditableNumberText())
    }

    @Test
    fun roundsAwaySpuriousPrecisionObservedOnAnItemCard() {
        assertEquals("1.75", 1.7500000000000002.toEditableNumberText())
    }

    @Test
    fun keepsSmallValuesOutOfScientificNotation() {
        assertEquals("0.0001", 0.0001.toEditableNumberText())
        assertEquals("0.0001", 1.0e-4.toEditableNumberText())
    }

    @Test
    fun wholeNumbersRenderWithoutADecimalPoint() {
        assertEquals("620", 620.0.toEditableNumberText())
        assertEquals("0", 0.0.toEditableNumberText())
        assertEquals("0", (-0.0).toEditableNumberText())
        assertEquals("-42", (-42.0).toEditableNumberText())
    }

    @Test
    fun genuineDecimalsSurviveUntouched() {
        assertEquals("12.5", 12.5.toEditableNumberText())
        assertEquals("3.2", 3.2.toEditableNumberText())
        assertEquals("100.5", 100.5.toEditableNumberText())
        assertEquals("-0.25", (-0.25).toEditableNumberText())
    }

    @Test
    fun largeWholeNumbersAreNotTruncatedThroughAnInt() {
        // The previous implementation went via toInt(), which saturated at Int.MAX_VALUE.
        assertEquals("3000000000", 3.0e9.toEditableNumberText())
    }

    @Test
    fun nullRendersAsAnEmptyFieldRatherThanAZero() {
        assertEquals("", (null as Double?).toEditableNumberTextOrEmpty())
        assertEquals("816.65", (816.6500000000000004 as Double?).toEditableNumberTextOrEmpty())
    }

    @Test
    fun nonFiniteValuesDoNotThrow() {
        assertEquals("NaN", Double.NaN.toEditableNumberText())
        assertEquals("Infinity", Double.POSITIVE_INFINITY.toEditableNumberText())
        assertEquals("-Infinity", Double.NEGATIVE_INFINITY.toEditableNumberText())
    }

    @Test
    fun exactDecimalsAreCanonicalisedWithoutBeingRounded() {
        // Portion scaling carries a twelve-decimal factor; the BigDecimal path must not round it away.
        assertEquals("0.333333333333", BigDecimal("0.333333333333").toEditableNumberText())
        assertEquals("200", BigDecimal("200.000").toEditableNumberText())
        assertEquals("0.0001", BigDecimal("1.0E-4").toEditableNumberText())
    }
}
