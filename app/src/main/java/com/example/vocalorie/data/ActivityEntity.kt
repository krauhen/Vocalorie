package com.example.vocalorie.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtEpochMillis: Long,
    val type: String,
    val title: String,
    val description: String,
    val caloriesBurnedKcal: Double,
    val durationMinutes: Int,
    val stepsCount: Int? = null,
)
