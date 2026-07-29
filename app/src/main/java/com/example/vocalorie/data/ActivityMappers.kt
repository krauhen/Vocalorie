package com.example.vocalorie.data

import com.example.vocalorie.model.ActivityType
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.model.toEditableNumberText

fun EditableActivityDraft.toEntity(createdAtEpochMillis: Long = System.currentTimeMillis()): ActivityEntity =
    ActivityEntity(
        createdAtEpochMillis = createdAtEpochMillis,
        type = requireNotNull(type).name,
        title = title.trim(),
        description = description.trim(),
        caloriesBurnedKcal = caloriesBurnedKcal.toNullableDouble() ?: 0.0,
        durationMinutes = durationMinutes.toNullableInt() ?: 0,
        stepsCount = steps.toNullableInt(),
    )

fun EditableActivityDraft.toEntity(id: Long, createdAtEpochMillis: Long): ActivityEntity =
    toEntity(createdAtEpochMillis = createdAtEpochMillis).copy(id = id)

fun ActivityEntity.toSavedActivity(): SavedActivity = SavedActivity(
    id = id,
    createdAtEpochMillis = createdAtEpochMillis,
    type = type.toActivityType(),
    title = title,
    description = description,
    caloriesBurnedKcal = caloriesBurnedKcal,
    durationMinutes = durationMinutes,
    stepsCount = stepsCount,
)

fun SavedActivity.toEditableDraft(): EditableActivityDraft = EditableActivityDraft(
    type = type,
    title = title,
    description = description,
    caloriesBurnedKcal = caloriesBurnedKcal.toEditableNumberText(),
    durationMinutes = durationMinutes.toString(),
    steps = stepsCount?.toString() ?: "",
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun String.toNullableDouble(): Double? = trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

private fun String.toNullableInt(): Int? = trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

/** An unrecognized persisted type name is neutral, not a run. */
private fun String.toActivityType(): ActivityType = runCatching { ActivityType.valueOf(this) }.getOrDefault(ActivityType.OTHER)
