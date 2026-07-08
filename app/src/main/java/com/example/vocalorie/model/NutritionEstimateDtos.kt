package com.example.vocalorie.model

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("NutritionSpikeResult")
@LLMDescription("A structured nutrition estimate that must be reviewed by a human before saving.")
data class NutritionSpikeResult(
    @property:LLMDescription("The original user food query.")
    val query: String,
    val items: List<FoodItemEstimate>,
    val totals: NutritionTotals,
    @property:LLMDescription("Source URL for online nutrition data; source must be a concrete http/https food-entry page URL when available, blank if no URL was used or only a generic homepage was available. Do not put plain source names like USDA without a URL.")
    val source: String,
    @property:LLMDescription("Assumptions made while estimating nutrition.")
    val assumptions: List<String>,
    @property:LLMDescription("Warnings and uncertainty notes for the user.")
    val warnings: List<String>,
    @property:LLMDescription("Overall confidence in the estimate.")
    val confidence: ConfidenceLevel,
    @property:LLMDescription("Always true for this spike because nutrition estimates require human review.")
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
