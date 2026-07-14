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
import com.example.vocalorie.model.NutritionAgentResult
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.settings.OpenAiModelChoice
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

object KoogNutritionAgent {
    val DEFAULT_SYSTEM_PROMPT: String = """
        You are Vocalorie's nutrition extraction helper.
        Estimate calories, approximate amount in g/ml, and nutrition-label values from the user's meal.
        This is a human-reviewed estimate, not authoritative nutrition data.
        Return strict JSON matching the requested schema.
        Every calorie estimate must include approximate amount in g/ml.
        When one or more images are attached, combine the photo with the full text query, including any amount like 200g, as one estimate.
        If the user text includes an amount like 100g, copy that amount into amountGml even when an image is attached.
        Include protein, carbohydrates, and fat, plus saturated fat, sugar, and salt for each item.
        Do not omit saturatedFatG, sugarG, or saltG; use 0.0 when the source data or the food itself indicates no meaningful amount.
        Meal totals are computed by the app from item rows; estimate item values only.
        Use grams and milliliters as approximately equivalent for amount summing.
        Do not return calories without macros and the nutrition-label fields.

        For food composition data, prefer this priority order of databases: German BLS, USDA, CoFID, Ciqual, Frida, AFCD, Swiss Food Composition Database, NEVO, Livsmedelsverket, CNF, Open Food Facts, FAO-INFOODS.
        Every food item's source must be a concrete http/https food-entry page URL from one of the recommended databases. If you cannot confidently identify a real URL for an item, leave source empty rather than naming a database.

        Split composite meals into individual food items. For example, estimate "coffee with milk" as two items: "black coffee" and "milk" with separate nutrition values.

        Generate a short, natural title (2-5 words) summarizing the whole meal, in German, e.g. "Hähnchen Caesar Salat".
        Always reply in German. Use German for all quantity descriptions, reasoning, assumptions, and warnings, regardless of the user's query language.
        Always mark needsHumanReview as true.
        The user may write the query in German, including German decimal commas like 1,5 and German units such as EL (tablespoon), TL (teaspoon), Stück (piece), Scheibe (slice), Prise (pinch), and Portion (portion); interpret these the same as their English equivalents when estimating amountGml.
        Recognize German food names and descriptions directly without needing to translate them first.
        """.trimIndent()

    val REQUIRED_SYSTEM_PROMPT_PHRASES: List<String> = listOf(
        "protein, carbohydrates, and fat",
        "amount in g/ml",
        "saturated fat, sugar, and salt",
        "Do not return calories without",
        "Meal totals are computed by the app from item rows",
        "item values only",
        "combine the photo with the full text query",
        "German decimal commas",
        "German BLS",
        "Every food item's source must be a concrete http/https food-entry page URL",
        "leave source empty rather than naming a database",
        "Split composite meals into individual food items",
        "Generate a short, natural title",
        "Always reply in German",
    )

    fun missingRequiredSystemPromptPhrases(prompt: String): List<String> =
        REQUIRED_SYSTEM_PROMPT_PHRASES.filterNot { prompt.contains(it) }

    suspend fun estimate(
        openAiApiKey: String,
        query: String,
        toolSettings: ToolSettings = ToolSettings(),
        imageAttachments: List<GalleryImageAttachment> = emptyList(),
    ): NutritionAgentResult = withContext(Dispatchers.IO) {
        val trimmedKey = openAiApiKey.trim()
        val trimmedQuery = query.trim()

        require(trimmedKey.isNotEmpty()) { "Enter an OpenAI API key." }
        require(trimmedQuery.isNotEmpty()) { "Enter a nutrition query." }

        runCatching { runKoog(trimmedKey, trimmedQuery, toolSettings, imageAttachments) }
            .getOrElse { throwable -> throw NutritionAgentException(throwable.toUserMessage(), throwable.toDiagnosticString(), throwable) }
    }

    private suspend fun runKoog(
        openAiApiKey: String,
        query: String,
        toolSettings: ToolSettings,
        imageAttachments: List<GalleryImageAttachment> = emptyList(),
    ): NutritionAgentResult {
        val model = when (toolSettings.openAiModelChoice) {
            OpenAiModelChoice.GPT4O -> OpenAIModels.Chat.GPT4o
            OpenAiModelChoice.GPT4OMINI -> OpenAIModels.Chat.GPT4oMini
            OpenAiModelChoice.GPT41MINI -> OpenAIModels.Chat.GPT4_1Mini
            OpenAiModelChoice.GPT54MINI -> OpenAIModels.Chat.GPT5_4Mini
        }
        val outputStructure = JsonStructure.create<NutritionAgentResult>(
            schemaGenerator = OpenAIBasicJsonSchemaGenerator,
            examples = listOf(sampleResult(query), sampleCucumberResult(query)),
        )
        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to OpenAILLMClient(
                apiKey = openAiApiKey,
                httpClientFactory = KtorKoogHttpClient.Factory(),
            ),
        )
        val effectiveSystemPrompt = toolSettings.systemPromptOverride?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT
        val prompt = prompt("vocalorie-nutrition-estimate", params = LLMParams(schema = outputStructure.schema)) {
            system(effectiveSystemPrompt)
            user {
                val amountHint = query.extractAmountHint()?.let { "Amount hint from the description: $it." }.orEmpty()
                text(
                    buildString {
                        append(
                            when {
                                imageAttachments.isEmpty() -> "Estimate this meal: $query"
                                imageAttachments.size == 1 -> "Estimate this meal from the attached photo together with the full text query${if (query.isNotBlank()) ": $query" else ""}."
                                else -> "Estimate this meal from the ${imageAttachments.size} attached photos together with the full text query${if (query.isNotBlank()) ": $query" else ""}."
                            }
                        )
                        if (amountHint.isNotBlank()) {
                            append('\n')
                            append(amountHint)
                            append(" Use that amount together with the image.")
                        }
                    }
                )
                imageAttachments.forEach { image(it.image) }
            }
        }

        val response = executor.execute(prompt, model, emptyList())
        val responseText = response.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
        if (responseText.isBlank()) throw IllegalStateException("Koog nutrition estimate returned an empty response.")

        return outputStructure.parse(responseText)
    }

    private fun sampleResult(query: String) = NutritionAgentResult(
        query = query,
        title = "Spiegeleier",
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
        assumptions = listOf("Foods use common serving sizes unless the query says otherwise."),
        warnings = listOf("Nutrition values are estimates and require human review."),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = true,
    )

    private fun sampleCucumberResult(query: String) = NutritionAgentResult(
        query = query,
        title = "Gurke",
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
        assumptions = listOf("Vegetables can use zero saturated fat when the nutrition source supports it."),
        warnings = listOf("Nutrition values are estimates and require human review."),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = true,
    )
}

class NutritionAgentException(
    message: String,
    val diagnostic: String,
    cause: Throwable,
) : Exception(message, cause)

@Serializable
private data class NutritionAgentRequest(val query: String)

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

        else -> raw.ifBlank { "Koog nutrition estimate failed." }
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

private fun String.extractAmountHint(): String? = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*(g|gram|grams|ml|mL|kg|l|el|tl|stück|stueck|scheibe[n]?|prise[n]?|portion(?:en)?)\b""")
    .find(this)
    ?.let { match ->
        val value = match.groupValues[1].replace(',', '.')
        val unit = match.groupValues[2].lowercase()
        "$value $unit"
    }
