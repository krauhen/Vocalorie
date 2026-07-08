package com.example.vocalorie.ai

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.base.structure.OpenAIBasicJsonSchemaGenerator
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.structure.json.JsonStructure
import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.FoodItemEstimate
import com.example.vocalorie.model.NutritionSpikeResult
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.settings.OpenAiModelChoice
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

object KoogNutritionSpike {
    suspend fun estimate(
        openAiApiKey: String,
        query: String,
        toolSettings: ToolSettings = ToolSettings(),
        imageAttachment: GalleryImageAttachment? = null,
    ): NutritionSpikeResult = withContext(Dispatchers.IO) {
        val trimmedKey = openAiApiKey.trim()
        val trimmedQuery = query.trim()

        require(trimmedKey.isNotEmpty()) { "Enter an OpenAI API key." }
        require(trimmedQuery.isNotEmpty()) { "Enter a nutrition query." }

        runCatching { runKoog(trimmedKey, trimmedQuery, toolSettings, imageAttachment) }
            .getOrElse { throwable -> throw NutritionSpikeException(throwable.toUserMessage(), throwable.toDiagnosticString(), throwable) }
    }

    private suspend fun runKoog(
        openAiApiKey: String,
        query: String,
        toolSettings: ToolSettings,
        imageAttachment: GalleryImageAttachment? = null,
    ): NutritionSpikeResult {
        val model = when (toolSettings.openAiModelChoice) {
            OpenAiModelChoice.GPT4O -> OpenAIModels.Chat.GPT4o
            OpenAiModelChoice.GPT4OMINI -> OpenAIModels.Chat.GPT4oMini
            OpenAiModelChoice.GPT41MINI -> OpenAIModels.Chat.GPT4_1Mini
            OpenAiModelChoice.GPT54MINI -> OpenAIModels.Chat.GPT5_4Mini
        }
        val outputStructure = JsonStructure.create<NutritionSpikeResult>(
            schemaGenerator = OpenAIBasicJsonSchemaGenerator,
            examples = listOf(sampleResult(query), sampleCucumberResult(query)),
        )
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to OpenAILLMClient(
                apiKey = openAiApiKey,
                httpClientFactory = KtorKoogHttpClient.Factory(),
            ),
        )
        val prompt = prompt("vocalorie-nutrition-spike", params = LLMParams(schema = outputStructure.schema)) {
            system(
                """
                You are Vocalorie's nutrition extraction helper.
                Estimate calories, approximate amount in g/ml, and nutrition-label values from the user's meal.
                This is a human-reviewed estimate, not authoritative nutrition data.
                Return strict JSON matching the requested schema.
                Every calorie estimate must include approximate amount in g/ml.
                When an image is attached, combine the photo with the full text query, including any amount like 200g, as one estimate.
                If the user text includes an amount like 100g, copy that amount into amountGml even when an image is attached.
                Include protein, carbohydrates, and fat, plus saturated fat, sugar, and salt for each item.
                Do not omit saturatedFatG, sugarG, or saltG; use 0.0 when the source data or the food itself indicates no meaningful amount.
                Meal totals are computed by the app from item rows; estimate item values only.
                Use grams and milliliters as approximately equivalent for amount summing.
                Do not return calories without macros and the nutrition-label fields.
                Prefer concrete food-entry source URLs over generic database homepages; if you only have a homepage like https://fdc.nal.usda.gov/, leave source blank.
                Always mark needsHumanReview as true.
                """.trimIndent(),
            )
            user {
                val amountHint = query.extractAmountHint()?.let { "Amount hint from the description: $it." }.orEmpty()
                text(
                    buildString {
                        append(if (imageAttachment == null) "Estimate this meal: $query" else "Estimate this meal from the attached photo together with the full text query${if (query.isNotBlank()) ": $query" else ""}.")
                        if (amountHint.isNotBlank()) {
                            append('\n')
                            append(amountHint)
                            append(" Use that amount together with the image.")
                        }
                    }
                )
                imageAttachment?.let { image(it.image) }
            }
        }

        val response = executor.execute(prompt, model, emptyList())
        val responseText = response.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
        if (responseText.isBlank()) throw IllegalStateException("Koog nutrition estimate returned an empty response.")

        return outputStructure.parse(responseText)
    }

    private fun sampleResult(query: String) = NutritionSpikeResult(
        query = query,
        items = listOf(
            FoodItemEstimate(
                name = "large egg",
                quantity = "2 eggs",
                amountGml = 100.0,
                caloriesKcal = 150.0,
                proteinG = 12.0,
                carbsG = 1.0,
                fatG = 10.0,
                saturatedFatG = 3.2,
                sugarG = 0.4,
                saltG = 0.3,
                source = "https://fdc.nal.usda.gov/fdc-app.html#/food-details/748967/nutrients",
                reasoning = "Typical large eggs vary by size and preparation.",
            ),
        ),
        totals = NutritionTotals(
            caloriesKcal = 150.0,
            amountGml = 100.0,
            proteinG = 12.0,
            carbsG = 1.0,
            fatG = 10.0,
            saturatedFatG = 3.2,
            sugarG = 0.4,
            saltG = 0.3,
        ),
        source = "https://fdc.nal.usda.gov/fdc-app.html#/food-details/748967/nutrients",
        assumptions = listOf("Foods use common serving sizes unless the query says otherwise."),
        warnings = listOf("Nutrition values are estimates and require human review."),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = true,
    )

    private fun sampleCucumberResult(query: String) = NutritionSpikeResult(
        query = query,
        items = listOf(
            FoodItemEstimate(
                name = "cucumber",
                quantity = "100 g",
                amountGml = 100.0,
                caloriesKcal = 15.0,
                proteinG = 0.7,
                carbsG = 3.6,
                fatG = 0.1,
                saturatedFatG = 0.0,
                sugarG = 1.7,
                saltG = 0.0,
                source = "https://fdc.nal.usda.gov/fdc-app.html#/food-details/11206/nutrients",
                reasoning = "Cucumber is mostly water and has negligible saturated fat.",
            ),
        ),
        totals = NutritionTotals(
            caloriesKcal = 15.0,
            amountGml = 100.0,
            proteinG = 0.7,
            carbsG = 3.6,
            fatG = 0.1,
            saturatedFatG = 0.0,
            sugarG = 1.7,
            saltG = 0.0,
        ),
        source = "https://fdc.nal.usda.gov/fdc-app.html#/food-details/11206/nutrients",
        assumptions = listOf("Vegetables can use zero saturated fat when the nutrition source supports it."),
        warnings = listOf("Nutrition values are estimates and require human review."),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = true,
    )
}

class NutritionSpikeException(
    message: String,
    val diagnostic: String,
    cause: Throwable,
) : Exception(message, cause)

@Serializable
private data class NutritionSpikeRequest(val query: String)

private fun Throwable.toUserMessage(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("401", ignoreCase = true) || raw.contains("unauthorized", ignoreCase = true) ->
            "OpenAI rejected the API key. Check the key and try again."

        raw.contains("network", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
            "Network or OpenAI request failed. Check connectivity and try again."

        raw.contains("serial", ignoreCase = true) || raw.contains("schema", ignoreCase = true) || raw.contains("structured", ignoreCase = true) ->
            "Structured output or serialization failed. The model response did not match the DTO contract."

        raw.contains("research tool call", ignoreCase = true) -> raw

        raw.contains("tool", ignoreCase = true) ->
            "Koog tool execution failed. The app-owned Brave Search or WebFetch tool could not complete."

        raw.contains("Koog", ignoreCase = true) || raw.contains("agent", ignoreCase = true) ->
            "Koog setup or agent execution failed."

        else -> raw.ifBlank { "Koog nutrition spike failed." }
    }
}

private fun Throwable.toDiagnosticString(): String = buildString {
    appendLine("Diagnostic detail, sanitized; API keys are not logged or included.")
    generateSequence(this@toDiagnosticString as Throwable?) { it.cause }
        .take(8)
        .forEachIndexed { index, throwable ->
            appendLine("${index + 1}. ${throwable::class.qualifiedName}: ${throwable.message.orEmpty().sanitizeForDisplay()}")
        }
}

private fun String.sanitizeForDisplay(): String =
    replace(Regex("sk-[A-Za-z0-9_-]+"), "sk-…redacted…")
        .replace(Regex("Bearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer …redacted…")

private fun String.extractAmountHint(): String? = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*(g|gram|grams|ml|mL|kg|l)\b""")
    .find(this)
    ?.let { match ->
        val value = match.groupValues[1].replace(',', '.')
        val unit = match.groupValues[2].lowercase()
        "$value $unit"
    }
