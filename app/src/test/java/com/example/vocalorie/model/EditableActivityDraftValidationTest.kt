package com.example.vocalorie.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the activity-save rule extracted out of the editor's save lambda: a STEPS entry derives
 * its calories and carries no duration, everything else takes the entered numbers, and each
 * rejection message is the one the user sees.
 */
class EditableActivityDraftValidationTest {

    private val kcalPerStep = 0.03

    private fun draft(
        type: ActivityType? = ActivityType.RUNNING,
        caloriesBurnedKcal: String = "300",
        durationMinutes: String = "30",
        steps: String = "",
    ): EditableActivityDraft = EditableActivityDraft(
        type = type,
        title = "Morning run",
        description = "",
        caloriesBurnedKcal = caloriesBurnedKcal,
        durationMinutes = durationMinutes,
        steps = steps,
        createdAtEpochMillis = 1_753_257_300_000L,
    )

    private fun valid(draft: EditableActivityDraft): ActivityDraftValidation.Valid {
        val result = draft.validate(kcalPerStep)
        assertTrue("expected $draft to validate, got $result", result is ActivityDraftValidation.Valid)
        return result as ActivityDraftValidation.Valid
    }

    private fun invalidMessage(draft: EditableActivityDraft): String {
        val result = draft.validate(kcalPerStep)
        assertTrue("expected $draft to be rejected, got $result", result is ActivityDraftValidation.Invalid)
        return (result as ActivityDraftValidation.Invalid).message
    }

    @Test
    fun aCompleteNonStepDraftValidates() {
        val validated = valid(draft())

        assertEquals(ActivityType.RUNNING, validated.type)
        assertEquals(300.0, validated.caloriesBurnedKcal, 0.0)
        assertEquals(30, validated.durationMinutes)
        assertNull(validated.stepsCount)
    }

    @Test
    fun surroundingWhitespaceAndDecimalCommaAreAccepted() {
        val validated = valid(draft(caloriesBurnedKcal = " 312,5 ", durationMinutes = " 45 "))

        assertEquals(312.5, validated.caloriesBurnedKcal, 0.0)
        assertEquals(45, validated.durationMinutes)
    }

    @Test
    fun zeroCaloriesAndZeroDurationAreAccepted() {
        val validated = valid(draft(caloriesBurnedKcal = "0", durationMinutes = "0"))

        assertEquals(0.0, validated.caloriesBurnedKcal, 0.0)
        assertEquals(0, validated.durationMinutes)
    }

    @Test
    fun negativeCaloriesAndDurationStayAcceptedBecauseOnlyStepsAreRangeChecked() {
        // Pins current behaviour: the only sign check the save path performs is on the step count.
        val validated = valid(draft(caloriesBurnedKcal = "-50", durationMinutes = "-10"))

        assertEquals(-50.0, validated.caloriesBurnedKcal, 0.0)
        assertEquals(-10, validated.durationMinutes)
    }

    @Test
    fun aStepsDraftDerivesCaloriesFromTheStepCountAndCarriesNoDuration() {
        val validated = valid(draft(type = ActivityType.STEPS, caloriesBurnedKcal = "", durationMinutes = "", steps = "10000"))

        assertEquals(ActivityType.STEPS, validated.type)
        assertEquals(300.0, validated.caloriesBurnedKcal, 1e-9)
        assertEquals(0, validated.durationMinutes)
        assertEquals(10_000, validated.stepsCount)
    }

    @Test
    fun aStepsDraftIgnoresEnteredCaloriesAndDuration() {
        val validated = valid(draft(type = ActivityType.STEPS, caloriesBurnedKcal = "9999", durationMinutes = "42", steps = " 5000 "))

        assertEquals(150.0, validated.caloriesBurnedKcal, 1e-9)
        assertEquals(0, validated.durationMinutes)
        assertEquals(5_000, validated.stepsCount)
    }

    @Test
    fun zeroStepsIsAccepted() {
        val validated = valid(draft(type = ActivityType.STEPS, caloriesBurnedKcal = "", durationMinutes = "", steps = "0"))

        assertEquals(0.0, validated.caloriesBurnedKcal, 0.0)
        assertEquals(0, validated.stepsCount)
    }

    @Test
    fun aNonStepDraftDoesNotCarryAStepCountEvenWhenOneWasTyped() {
        val validated = valid(draft(steps = "10000"))

        assertNull(validated.stepsCount)
    }

    @Test
    fun aMissingTypeIsRejectedFirst() {
        // Every other field is unusable too; the type message is the one the user gets.
        assertEquals(
            "Choose an activity type before saving.",
            invalidMessage(draft(type = null, caloriesBurnedKcal = "", durationMinutes = "")),
        )
    }

    @Test
    fun blankNonNumericOrNegativeStepsAreRejected() {
        listOf("", "   ", "10 000", "5.5", "-1").forEach { steps ->
            assertEquals(
                "Enter your step count as a whole number.",
                invalidMessage(draft(type = ActivityType.STEPS, caloriesBurnedKcal = "", durationMinutes = "", steps = steps)),
            )
        }
    }

    @Test
    fun blankOrNonNumericCaloriesAreRejected() {
        listOf("", "  ", "lots").forEach { calories ->
            assertEquals("Enter calories burned as a number.", invalidMessage(draft(caloriesBurnedKcal = calories)))
        }
    }

    @Test
    fun blankNonNumericOrFractionalDurationIsRejected() {
        listOf("", "  ", "half an hour", "30.5").forEach { duration ->
            assertEquals("Enter duration in whole minutes.", invalidMessage(draft(durationMinutes = duration)))
        }
    }

    @Test
    fun anInvalidCalorieValueIsReportedBeforeAnInvalidDuration() {
        assertEquals(
            "Enter calories burned as a number.",
            invalidMessage(draft(caloriesBurnedKcal = "", durationMinutes = "")),
        )
    }

    @Test
    fun everySelectableTypeValidatesWithTheSameEnteredNumbers() {
        SELECTABLE_ACTIVITY_TYPES.filterNot { it == ActivityType.STEPS }.forEach { type ->
            val validated = valid(draft(type = type))
            assertEquals(type, validated.type)
            assertEquals(300.0, validated.caloriesBurnedKcal, 0.0)
        }
    }
}
