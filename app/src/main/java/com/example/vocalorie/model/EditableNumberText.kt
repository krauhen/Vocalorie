package com.example.vocalorie.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Decimal places kept before canonicalising. Ten is far beyond any real nutrition precision and
 * short enough to erase the binary artefacts a chain of `Double` arithmetic leaves behind.
 */
private const val EDITABLE_NUMBER_SCALE = 10

/**
 * The single formatter for a number that goes into an editable text field.
 *
 * It rounds first and canonicalises second, because a value such as `816.6500000000000004` is a
 * genuinely distinct `Double` whose shortest round-trip text really is that string — stripping
 * trailing zeros alone would not clean it up. [BigDecimal.valueOf] is used deliberately: it goes
 * through that shortest form, whereas the `BigDecimal(Double)` constructor would expand the full
 * binary value. [BigDecimal.toPlainString] keeps small values out of scientific notation, so
 * `0.0001` stays `0.0001` instead of becoming `1.0E-4`.
 *
 * This is the *editable* formatter. Read-only labels use `formatNullable()`, which deliberately
 * rounds to one decimal and says "unknown" for a missing value.
 */
fun Double.toEditableNumberText(): String =
    if (!isFinite()) toString() else BigDecimal.valueOf(this).roundedToEditableNumberText()

/** A missing value leaves the field empty rather than reading as a real `0`. */
fun Double?.toEditableNumberTextOrEmpty(): String = this?.toEditableNumberText().orEmpty()

/**
 * Canonicalises an already-exact decimal without rounding it, so portion scaling keeps all twelve
 * decimals its factor carries.
 */
fun BigDecimal.toEditableNumberText(): String = stripTrailingZeros().toPlainString()

private fun BigDecimal.roundedToEditableNumberText(): String =
    setScale(EDITABLE_NUMBER_SCALE, RoundingMode.HALF_UP).toEditableNumberText()
