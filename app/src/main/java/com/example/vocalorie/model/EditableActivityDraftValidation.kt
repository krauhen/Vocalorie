package com.example.vocalorie.model

/**
 * Outcome of [EditableActivityDraft.validate]: either the persistable values derived from the
 * draft's text fields, or the message to show the user.
 */
sealed interface ActivityDraftValidation {
    /**
     * The values a save must write, already resolved for the draft's type: a STEPS entry burns
     * [caloriesBurnedKcal] derived from its step count and carries no duration, everything else
     * carries the entered calories and duration and no step count.
     */
    data class Valid(
        val type: ActivityType,
        val caloriesBurnedKcal: Double,
        val durationMinutes: Int,
        val stepsCount: Int?,
    ) : ActivityDraftValidation

    data class Invalid(val message: String) : ActivityDraftValidation
}

/**
 * Validates an activity draft before it is persisted, resolving the numeric fields the entity needs.
 *
 * Pure: no clock, no storage, no Android — the created-at fallback stays with the caller that owns
 * the timestamp. [kcalPerStep] is the per-step burn factor from Settings, used only for STEPS
 * entries, whose calories are derived rather than entered.
 */
fun EditableActivityDraft.validate(kcalPerStep: Double): ActivityDraftValidation {
    val activityType = type
    val isSteps = activityType == ActivityType.STEPS
    val stepCount = steps.trim().toIntOrNull()
    val calories = if (isSteps) {
        stepCount?.let { stepsBurnKcal(it, kcalPerStep) }
    } else {
        caloriesBurnedKcal.trim().replace(',', '.').toDoubleOrNull()
    }
    val duration = if (isSteps) 0 else durationMinutes.trim().toIntOrNull()
    return when {
        activityType == null -> ActivityDraftValidation.Invalid(MISSING_ACTIVITY_TYPE_MESSAGE)
        isSteps && (stepCount == null || stepCount < 0) -> ActivityDraftValidation.Invalid(INVALID_STEP_COUNT_MESSAGE)
        calories == null -> ActivityDraftValidation.Invalid(INVALID_CALORIES_MESSAGE)
        duration == null -> ActivityDraftValidation.Invalid(INVALID_DURATION_MESSAGE)
        else -> ActivityDraftValidation.Valid(
            type = activityType,
            caloriesBurnedKcal = calories,
            durationMinutes = duration,
            stepsCount = if (isSteps) stepCount else null,
        )
    }
}

private const val MISSING_ACTIVITY_TYPE_MESSAGE = "Choose an activity type before saving."
private const val INVALID_STEP_COUNT_MESSAGE = "Enter your step count as a whole number."
private const val INVALID_CALORIES_MESSAGE = "Enter calories burned as a number."
private const val INVALID_DURATION_MESSAGE = "Enter duration in whole minutes."
