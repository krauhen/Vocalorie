package com.example.vocalorie.data

import com.example.vocalorie.model.ActivityType
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.SELECTABLE_ACTIVITY_TYPES
import com.example.vocalorie.model.displayName
import com.example.vocalorie.model.stepsBurnKcal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActivityMappersTest {
    @Test
    fun editableActivityDraftRoundTripsThroughEntityAndBack() {
        val draft = EditableActivityDraft(
            type = ActivityType.RUNNING,
            title = "Run",
            description = "Morning run",
            caloriesBurnedKcal = "321.5",
            durationMinutes = "42",
            createdAtEpochMillis = 123L,
        )

        val saved = draft.toEntity(id = 7L, createdAtEpochMillis = 123L).toSavedActivity()

        assertEquals(7L, saved.id)
        assertEquals(ActivityType.RUNNING, saved.type)
        assertEquals(321.5, saved.caloriesBurnedKcal, 0.0)
        assertEquals(42, saved.durationMinutes)
    }

    @Test
    fun savedActivityBecomesEditableDraft() {
        val draft = EditableActivityDraft(
            type = ActivityType.WALKING,
            title = "Walk",
            description = "Lunch break",
            caloriesBurnedKcal = "100",
            durationMinutes = "30",
            createdAtEpochMillis = 123L,
        )

        val editable = draft.toEntity(id = 1L, createdAtEpochMillis = 123L).toSavedActivity().toEditableDraft()

        assertEquals(ActivityType.WALKING, editable.type)
        assertEquals("Walk", editable.title)
        assertEquals("Lunch break", editable.description)
        assertEquals("100", editable.caloriesBurnedKcal)
        assertEquals("30", editable.durationMinutes)
    }

    @Test
    fun stepsCountRoundTripsThroughEntityAndBack() {
        val draft = EditableActivityDraft(
            type = ActivityType.STEPS,
            title = "Steps",
            description = "",
            caloriesBurnedKcal = "280",
            durationMinutes = "0",
            steps = "8000",
            createdAtEpochMillis = 123L,
        )

        val editable = draft.toEntity(id = 3L, createdAtEpochMillis = 123L).toSavedActivity().toEditableDraft()

        assertEquals(ActivityType.STEPS, editable.type)
        assertEquals("8000", editable.steps)
    }

    @Test
    fun stepsBurnScalesWithPerStepFactor() {
        assertEquals(280.0, stepsBurnKcal(8000, 0.035), 1e-9)
        assertEquals(0.0, stepsBurnKcal(0, 0.035), 0.0)
        // Negative step counts are clamped to zero.
        assertEquals(0.0, stepsBurnKcal(-100, 0.035), 0.0)
    }

    @Test
    fun unknownPersistedTypeBecomesOtherRatherThanRunning() {
        val entity = ActivityEntity(
            id = 5L,
            createdAtEpochMillis = 123L,
            type = "PARAGLIDING",
            title = "Flight",
            description = "",
            caloriesBurnedKcal = 200.0,
            durationMinutes = 60,
        )

        val saved = entity.toSavedActivity()

        assertEquals(ActivityType.OTHER, saved.type)
        assertNotEquals(ActivityType.RUNNING, saved.type)
    }

    @Test
    fun blankPersistedTypeBecomesOther() {
        val entity = ActivityEntity(
            id = 6L,
            createdAtEpochMillis = 123L,
            type = "",
            title = "",
            description = "",
            caloriesBurnedKcal = 0.0,
            durationMinutes = 0,
        )

        assertEquals(ActivityType.OTHER, entity.toSavedActivity().type)
    }

    @Test
    fun otherIsNeverOfferedAsASelectableActivityType() {
        // The picker renders SELECTABLE_ACTIVITY_TYPES, so OTHER must not add a visible chip.
        assertFalse(ActivityType.OTHER in SELECTABLE_ACTIVITY_TYPES)
        assertEquals(ActivityType.entries.size - 1, SELECTABLE_ACTIVITY_TYPES.size)
        assertEquals(ActivityType.entries.filterNot { it == ActivityType.OTHER }, SELECTABLE_ACTIVITY_TYPES)
    }

    @Test
    fun otherHasANeutralDisplayName() {
        // Its icon is covered by the exhaustive `when` in activityTypeIcon(), which is a compile-time
        // guarantee; rendering an ImageVector is left to on-device verification.
        assertEquals("Other", ActivityType.OTHER.displayName())
    }
}
