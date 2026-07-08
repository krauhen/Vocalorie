package com.example.vocalorie.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtEpochMillis: Long,
    val title: String,
    val query: String,
    val itemsJson: String,
    val caloriesKcal: Double?,
    val amountGml: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val saltG: Double?,
    val source: String?,
    val assumptionsText: String,
    val warningsText: String,
    val confidence: String,
    val needsHumanReview: Boolean,
)
