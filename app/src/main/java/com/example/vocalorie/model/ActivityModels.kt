package com.example.vocalorie.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class ActivityType {
    RUNNING,
    WALKING,
    BIKE,
    KETTLEBELL,
    GYM,
    HIKING,
    SWIMMING,
    STEPS,

    /**
     * Neutral fallback for a persisted type name this build does not recognize. Never offered as a
     * choice (see [SELECTABLE_ACTIVITY_TYPES]) — an unreadable row must not masquerade as a run.
     */
    OTHER,
}

/**
 * The activity types a user can pick. Excludes [ActivityType.OTHER], which exists only to represent
 * unreadable persisted data and must stay invisible in the picker.
 */
val SELECTABLE_ACTIVITY_TYPES: List<ActivityType> = ActivityType.entries.filterNot { it == ActivityType.OTHER }

fun ActivityType.displayName(): String = when (this) {
    ActivityType.RUNNING -> "Running"
    ActivityType.WALKING -> "Walking"
    ActivityType.BIKE -> "Bike"
    ActivityType.KETTLEBELL -> "Kettlebell"
    ActivityType.GYM -> "Gym"
    ActivityType.HIKING -> "Hiking"
    ActivityType.SWIMMING -> "Swimming"
    ActivityType.STEPS -> "Steps"
    ActivityType.OTHER -> "Other"
}

data class EditableActivityDraft(
    val type: ActivityType?,
    val title: String,
    val description: String,
    val caloriesBurnedKcal: String,
    val durationMinutes: String,
    val steps: String = "",
    val createdAtEpochMillis: Long? = null,
)

data class SavedActivity(
    val id: Long,
    val createdAtEpochMillis: Long,
    val type: ActivityType,
    val title: String,
    val description: String,
    val caloriesBurnedKcal: Double,
    val durationMinutes: Int,
    val stepsCount: Int? = null,
)

/** Calories burned for a manual step entry, using the per-step factor from Settings. */
fun stepsBurnKcal(steps: Int, kcalPerStep: Double): Double = steps.coerceAtLeast(0) * kcalPerStep

/** Default time-of-day for step entries: steps are typically logged at the end of the day. */
private val STEPS_DEFAULT_TIME: LocalTime = LocalTime.of(23, 59)

/** Returns [epochMillis] moved to 23:59 on the same calendar day in [zone]. */
fun endOfDayMillis(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(epochMillis)
        .atZone(zone)
        .toLocalDate()
        .atTime(STEPS_DEFAULT_TIME)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

/**
 * Applies add-form defaults when the activity type changes to a new non-null value:
 * pre-fills a blank title with the type's display name (never overwriting text the user
 * already entered) and defaults STEPS activities to 23:59 on the selected day. Title and
 * time stay editable, and a later type change does not overwrite them. Intended for the
 * add flow only — editing an existing activity must not call this.
 */
fun applyAddFormTypeChange(
    previous: EditableActivityDraft,
    updated: EditableActivityDraft,
    zone: ZoneId = ZoneId.systemDefault(),
): EditableActivityDraft {
    val newType = updated.type
    if (newType == null || newType == previous.type) return updated
    var result = updated
    if (result.title.isBlank()) {
        result = result.copy(title = newType.displayName())
    }
    if (newType == ActivityType.STEPS) {
        result.createdAtEpochMillis?.let { millis ->
            result = result.copy(createdAtEpochMillis = endOfDayMillis(millis, zone))
        }
    }
    return result
}
