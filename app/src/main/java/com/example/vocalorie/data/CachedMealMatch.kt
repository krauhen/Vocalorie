package com.example.vocalorie.data

import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.SavedMeal

data class CachedMealMatch(
    val meal: SavedMeal,
    val draft: EditableMealDraft,
)
