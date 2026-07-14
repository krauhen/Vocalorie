package com.example.vocalorie.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("NutritionAgentResult")
@LLMDescription("A structured nutrition estimate that must be reviewed by a human before saving.")
data class NutritionAgentResult(
    @property:LLMDescription("The original user food query.")
    val query: String,
    @property:LLMDescription("A short, natural-language title for the whole meal, e.g. \"Chicken Caesar Salad\".")
    val title: String,
    val items: List<FoodItemEstimate>,
    val totals: NutritionTotals,
    @property:LLMDescription("Assumptions made while estimating nutrition.")
    val assumptions: List<String>,
    @property:LLMDescription("Warnings and uncertainty notes for the user.")
    val warnings: List<String>,
    @property:LLMDescription("Overall confidence in the estimate.")
    val confidence: ConfidenceLevel,
    @property:LLMDescription("Always true because nutrition estimates require human review.")
    val needsHumanReview: Boolean,
)

@Serializable
data class FoodItemEstimate(
    val name: String,
    val quantity: String,
    val amountGml: Double? = null,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val saturatedFatG: Double? = null,
    val sugarG: Double? = null,
    val saltG: Double? = null,
    @property:LLMDescription("source must be a concrete http/https food-entry page URL, or empty if no confident URL is available")
    val source: String = "",
    val reasoning: String = "",
)

@Serializable
data class NutritionTotals(
    val caloriesKcal: Double?,
    val amountGml: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val saltG: Double?,
)

@Serializable
enum class ConfidenceLevel {
    LOW,
    MEDIUM,
    HIGH,
}
