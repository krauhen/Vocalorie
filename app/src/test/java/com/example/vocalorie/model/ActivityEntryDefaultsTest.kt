package com.example.vocalorie.model

import org.junit.Assert.assertEquals
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class ActivityEntryDefaultsTest {
    private val zone: ZoneId = ZoneId.of("Europe/Zurich")
    // 2026-07-23 09:15 local time.
    private val morning: Long = LocalDateTime.of(2026, 7, 23, 9, 15)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun addDraft(): EditableActivityDraft = EditableActivityDraft(
        type = null,
        title = "",
        description = "",
        caloriesBurnedKcal = "",
        durationMinutes = "",
        steps = "",
        createdAtEpochMillis = morning,
    )

    private fun localTimeOf(epochMillis: Long): Pair<Int, Int> =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).let { it.hour to it.minute }

    @Test
    fun titlePreFillsWithTypeDisplayNamePerType() {
        for (type in ActivityType.entries) {
            val previous = addDraft()
            val result = applyAddFormTypeChange(previous, previous.copy(type = type), zone)
            assertEquals(type.displayName(), result.title)
        }
    }

    @Test
    fun preFilledTitleIsNotOverwrittenByLaterTypeChange() {
        val previous = addDraft()
        val afterRunning = applyAddFormTypeChange(previous, previous.copy(type = ActivityType.RUNNING), zone)
        assertEquals("Running", afterRunning.title)

        // Changing the type again preserves the existing (non-blank) title.
        val afterBike = applyAddFormTypeChange(afterRunning, afterRunning.copy(type = ActivityType.BIKE), zone)
        assertEquals("Running", afterBike.title)
    }

    @Test
    fun editedTitleIsPreserved() {
        val previous = addDraft().copy(type = ActivityType.RUNNING, title = "Morning run")
        val result = applyAddFormTypeChange(previous, previous.copy(type = ActivityType.BIKE), zone)
        assertEquals("Morning run", result.title)
    }

    @Test
    fun stepsSelectionDefaultsTimeTo2359OnSelectedDay() {
        val previous = addDraft()
        val result = applyAddFormTypeChange(previous, previous.copy(type = ActivityType.STEPS), zone)
        assertEquals(23 to 59, localTimeOf(result.createdAtEpochMillis!!))
    }

    @Test
    fun nonStepSelectionKeepsExistingTime() {
        val previous = addDraft()
        val result = applyAddFormTypeChange(previous, previous.copy(type = ActivityType.RUNNING), zone)
        assertEquals(9 to 15, localTimeOf(result.createdAtEpochMillis!!))
    }

    @Test
    fun noOpWhenTypeUnchanged() {
        val previous = addDraft().copy(type = ActivityType.RUNNING, title = "Run")
        // A non-type field change must not trigger any defaulting.
        val updated = previous.copy(description = "Park loop")
        val result = applyAddFormTypeChange(previous, updated, zone)
        assertEquals(updated, result)
    }
}
