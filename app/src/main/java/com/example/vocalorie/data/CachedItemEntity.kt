package com.example.vocalorie.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached food item per normalized item name, kept separate from meal history. Nutrition is
 * stored normalized to a 100 g/ml basis so it can be scaled to any requested amount on reuse.
 * Populated only when a reviewed meal is saved.
 */
@Entity(tableName = "cached_items")
data class CachedItemEntity(
    @PrimaryKey val normalizedName: String,
    val displayName: String,
    val caloriesKcalPer100: Double?,
    val proteinGPer100: Double?,
    val carbsGPer100: Double?,
    val fatGPer100: Double?,
    val saturatedFatGPer100: Double?,
    val sugarGPer100: Double?,
    val saltGPer100: Double?,
    val source: String,
    val reasoning: String,
)
