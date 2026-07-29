package com.example.vocalorie.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One authoritative cached meal per normalized meal-query key, kept separate from the
 * [MealEntity] history table. Populated only when a reviewed meal is saved; reused for exact
 * normalized-key matches. There is no id/createdAt here — the key is the identity.
 */
@Serializable
@Entity(tableName = "cached_meals")
data class CachedMealEntity(
    @PrimaryKey val normalizedKey: String,
    val title: String,
    val query: String,
    val itemsJson: String,
    val assumptionsText: String,
    val warningsText: String,
    val confidence: String,
    val needsHumanReview: Boolean,
    /**
     * Food-type category name, mirroring [MealEntity.category]. Defaults to `OTHER` so cache rows
     * written before this column existed (and backup files exported before it) resolve neutrally.
     */
    val category: String = "OTHER",
)
