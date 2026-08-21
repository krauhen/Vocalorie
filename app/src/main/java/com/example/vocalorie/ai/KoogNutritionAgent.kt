package com.example.vocalorie.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.structure.OpenAIBasicJsonSchemaGenerator
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.structure.json.JsonStructure
import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.FoodItemEstimate
import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.model.NutritionAgentResult
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.tools.BraveSearchTool
import com.example.vocalorie.tools.HttpTextFetcher
import com.example.vocalorie.tools.KtorHttpTextFetcher
import com.example.vocalorie.tools.vocalorieToolRegistry
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The nutrition-estimation seam used by the UI.
 *
 * Exists so the capture flow depends on an interface rather than a concrete Koog implementation,
 * and so the estimate step can be replaced in tests without network access.
 */
interface NutritionEstimator {
    suspend fun estimate(
        openAiApiKey: String,
        query: String,
        toolSettings: ToolSettings = ToolSettings(),
        imageAttachments: List<GalleryImageAttachment> = emptyList(),
        onProgress: (EstimationProgress) -> Unit = {},
    ): NutritionEstimateOutcome
}

/**
 * A completed estimate plus whatever went wrong on the way to it.
 *
 * A failed grounding pass still yields a usable ungrounded estimate, but the failure is carried
 * here instead of being discarded, so the UI can say the estimate is unsourced *because grounding
 * failed* rather than presenting it as a pass that simply found nothing.
 */
data class NutritionEstimateOutcome(
    val result: NutritionAgentResult,
    val groundingFailureMessage: String? = null,
    val groundingFailureDiagnostic: String? = null,
)

class KoogNutritionAgent(
    /**
     * HTTP seam handed to the research tool layer, so grounding can be driven by a test double
     * instead of live network calls. Defaults to the process-wide client.
     */
    private val httpTextFetcher: HttpTextFetcher = KtorHttpTextFetcher.shared,
) : NutritionEstimator {

    override suspend fun estimate(
        openAiApiKey: String,
        query: String,
        toolSettings: ToolSettings,
        imageAttachments: List<GalleryImageAttachment>,
        onProgress: (EstimationProgress) -> Unit,
    ): NutritionEstimateOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedKey = openAiApiKey.trim()
            val trimmedQuery = query.trim()
            if (trimmedKey.isEmpty()) throw IllegalArgumentException("Enter an OpenAI API key.")
            if (trimmedQuery.isEmpty()) throw IllegalArgumentException("Enter a nutrition query.")
            runKoog(trimmedKey, trimmedQuery, toolSettings, imageAttachments, onProgress)
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            throw NutritionAgentException(throwable.toUserMessage(), throwable.toDiagnosticString(), throwable)
        }
    }

    private suspend fun runKoog(
        openAiApiKey: String,
        query: String,
        toolSettings: ToolSettings,
        imageAttachments: List<GalleryImageAttachment> = emptyList(),
        onProgress: (EstimationProgress) -> Unit = {},
    ): NutritionEstimateOutcome {
        val model = toolSettings.openAiModelChoice.model
        val outputStructure = JsonStructure.create<NutritionAgentResult>(
            schemaGenerator = OpenAIBasicJsonSchemaGenerator,
            examples = listOf(sampleResult(query), sampleCucumberResult(query)),
        )
        val executor = promptExecutors.get(openAiApiKey)
        val groundingEnabled = toolSettings.hasBraveApiKey && toolSettings.maxResearchToolCalls > 0
        val fetchedUrls = newFetchedUrlSet()
        var groundingFailure: Throwable? = null
        onProgress(EstimationProgress.Preparing)
        val researchNotes = if (groundingEnabled) {
            try {
                runGroundingAgent(executor, model, query, toolSettings, fetchedUrls, onProgress)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                groundingFailure = throwable
                fetchedUrls.clear()
                ""
            }
        } else {
            ""
        }

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
                        if (researchNotes.isNotBlank()) {
                            append("\n\nVerified research notes (use these real source URLs when relevant):\n")
                            append(researchNotes)
                        }
                    }
                )
                imageAttachments.forEach { image(it.image) }
            }
        }

        // Bounded in time and retried on transient failures only, so an unresponsive request can
        // never pin the capture flow's loading state.
        onProgress(EstimationProgress.CalculatingNutrition)
        val response = withBoundedRetry {
            withTimeoutOrNull(ESTIMATE_REQUEST_TIMEOUT_MS) {
                executor.execute(prompt, model, emptyList())
            } ?: throw NutritionEstimateTimeoutException(ESTIMATE_REQUEST_TIMEOUT_MS)
        }
        val responseText = response.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
        if (responseText.isBlank()) throw IllegalStateException("Koog nutrition estimate returned an empty response.")

        return buildEstimateOutcome(
            result = outputStructure.parse(responseText),
            fetchedUrls = fetchedUrls,
            groundingEnabled = groundingEnabled,
            groundingFailure = groundingFailure,
        )
    }

    private suspend fun runGroundingAgent(
        executor: MultiLLMPromptExecutor,
        model: LLModel,
        query: String,
        toolSettings: ToolSettings,
        fetchedUrls: MutableSet<String>,
        onProgress: (EstimationProgress) -> Unit,
    ): String {
        // Collect a URL only once web_fetch has actually retrieved it with a 2xx response,
        // so a guessed URL that 404s can never be treated as a real source.
        val toolRegistry = vocalorieToolRegistry(
            settings = toolSettings,
            fetcher = httpTextFetcher,
            onUrlFetched = { fetchedUrl ->
                normalizeSourceUrl(fetchedUrl)?.let {
                    fetchedUrls.add(it)
                    onProgress(EstimationProgress.ReadingSource(it))
                }
            },
        )
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            toolRegistry = toolRegistry,
            systemPrompt = RESEARCH_SYSTEM_PROMPT,
            temperature = 0.0,
            maxIterations = toolSettings.maxAgentIterations,
        ) {
            handleEvents {
                onToolCallStarting { context ->
                    if (context.toolName == BraveSearchTool.TOOL_NAME) onProgress(EstimationProgress.SearchingSources)
                }
            }
        }
        val researchInput = buildString {
            append("Research real, concrete food-composition and nutrition pages for the foods in this meal query, ")
            append("then report the exact page URLs you fetched with the key nutrition values you found:\n")
            append(query)
        }
        onProgress(EstimationProgress.SearchingSources)
        return agent.run(researchInput)
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
        category = MealCategory.MEAL,
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
        category = MealCategory.OTHER,
    )

    companion object {
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

            Classify the whole meal into exactly one category: MEAL, SNACK, DRINK, DESSERT, or OTHER. Use DRINK for beverages (coffee, juice, soda, alcohol), DESSERT for sweets, SNACK for small between-meal items, MEAL for full meals, and OTHER only when none clearly applies.
            Generate a short, natural title (2-5 words) summarizing the whole meal, in German, e.g. "Hähnchen Caesar Salat".
            Always reply in German. Use German for all quantity descriptions, reasoning, assumptions, and warnings, regardless of the user's query language.
            Always mark needsHumanReview as true.
            The user may write the query in German, including German decimal commas like 1,5 and German units such as EL (tablespoon), TL (teaspoon), Stück (piece), Scheibe (slice), Prise (pinch), and Portion (portion); interpret these the same as their English equivalents when estimating amountGml.
            Recognize German food names and descriptions directly without needing to translate them first.
            """.trimIndent()

        private val RESEARCH_SYSTEM_PROMPT: String = """
            You are Vocalorie's nutrition research assistant.
            Use the brave_search tool to find candidate food-composition and nutrition pages for the foods in the user's meal query, then use the web_fetch tool to open and read the most relevant pages.
            Only report source URLs that you actually fetched with web_fetch; never invent, complete, or guess URLs from memory.
            Prefer reputable food-composition databases such as German BLS, USDA, CoFID, Ciqual, Frida, AFCD, Swiss Food Composition Database, NEVO, Livsmedelsverket, CNF, Open Food Facts, and FAO-INFOODS.
            When done, list each food together with the exact URL you fetched and the key nutrition values you found there.
            If you cannot find or fetch a reliable page for a food, say so plainly instead of guessing a URL.
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
            "Classify the whole meal into exactly one category",
            "Generate a short, natural title",
            "Always reply in German",
        )

        /** Shown when a grounding pass was attempted and failed, so the estimate is unsourced by accident. */
        internal const val GROUNDING_FAILURE_MESSAGE: String =
            "Source research failed, so this estimate has no verified sources."

        /** Hard cap on one estimate request, well below Koog's 900 s client default. */
        internal const val ESTIMATE_REQUEST_TIMEOUT_MS: Long = 90_000L
        internal const val ESTIMATE_CONNECT_TIMEOUT_MS: Long = 20_000L

        /** Total attempts, including the first. Only transient failures consume an extra attempt. */
        internal const val ESTIMATE_MAX_ATTEMPTS: Int = 3
        internal const val ESTIMATE_RETRY_BACKOFF_MS: Long = 750L

        /**
         * One executor per API key for the whole process. Building a Ktor engine per estimate leaked
         * engine threads; the executor is only rebuilt when the key actually changes, and the
         * superseded one is closed.
         */
        internal val promptExecutors = KeyedResourceCache<MultiLLMPromptExecutor>(
            create = { apiKey ->
                MultiLLMPromptExecutor(
                    LLMProvider.OpenAI to OpenAILLMClient(
                        apiKey = apiKey,
                        settings = OpenAIClientSettings(
                            timeoutConfig = ConnectionTimeoutConfig(
                                requestTimeoutMillis = ESTIMATE_REQUEST_TIMEOUT_MS,
                                connectTimeoutMillis = ESTIMATE_CONNECT_TIMEOUT_MS,
                                socketTimeoutMillis = ESTIMATE_REQUEST_TIMEOUT_MS,
                            ),
                        ),
                        httpClientFactory = KtorKoogHttpClient.Factory(),
                    ),
                )
            },
            dispose = { it.close() },
        )

        fun missingRequiredSystemPromptPhrases(prompt: String): List<String> =
            REQUIRED_SYSTEM_PROMPT_PHRASES.filterNot { prompt.contains(it) }
    }
}

class NutritionAgentException(
    message: String,
    val diagnostic: String,
    cause: Throwable,
) : Exception(message, cause)

/** Raised when one estimate attempt outruns [KoogNutritionAgent.ESTIMATE_REQUEST_TIMEOUT_MS]. */
internal class NutritionEstimateTimeoutException(timeoutMillis: Long) :
    Exception("OpenAI estimate request hit the ${timeoutMillis} ms timeout before a response arrived.")

/**
 * Holds one resource per key, rebuilding only when the key changes and closing the superseded one.
 *
 * Synchronized rather than lock-free because creation is expensive and must happen exactly once per
 * key change, and because a discarded resource owns IO threads that have to be released.
 */
internal class KeyedResourceCache<T : Any>(
    private val create: (String) -> T,
    private val dispose: (T) -> Unit = {},
) {
    private val lock = Any()
    private var currentKey: String? = null
    private var current: T? = null

    fun get(key: String): T = synchronized(lock) {
        val existing = current
        if (existing != null && currentKey == key) return existing
        // Create before discarding, so a failed rebuild leaves the previous resource usable.
        val replacement = create(key)
        current = replacement
        currentKey = key
        if (existing != null) runCatching { dispose(existing) }
        replacement
    }
}

/** Fetch callbacks can land concurrently, so no successfully fetched URL may be lost to a race. */
internal fun newFetchedUrlSet(): MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

internal suspend fun <T> withBoundedRetry(
    maxAttempts: Int = KoogNutritionAgent.ESTIMATE_MAX_ATTEMPTS,
    backoffMillis: Long = KoogNutritionAgent.ESTIMATE_RETRY_BACKOFF_MS,
    awaitBackoff: suspend (Long) -> Unit = { delay(it) },
    block: suspend (attempt: Int) -> T,
): T {
    var attempt = 1
    while (true) {
        try {
            return block(attempt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (attempt >= maxAttempts || !throwable.isRetryableEstimateFailure()) throw throwable
            awaitBackoff(backoffMillis * attempt)
            attempt++
        }
    }
}

internal fun buildEstimateOutcome(
    result: NutritionAgentResult,
    fetchedUrls: Set<String>,
    groundingEnabled: Boolean,
    groundingFailure: Throwable?,
): NutritionEstimateOutcome = NutritionEstimateOutcome(
    result = result.withVerifiedSources(fetchedUrls, groundingEnabled && groundingFailure == null),
    groundingFailureMessage = groundingFailure?.let { KoogNutritionAgent.GROUNDING_FAILURE_MESSAGE },
    groundingFailureDiagnostic = groundingFailure?.toDiagnosticString(),
)

internal fun NutritionAgentResult.withVerifiedSources(
    fetchedUrls: Set<String>,
    groundingEnabled: Boolean,
): NutritionAgentResult {
    if (!groundingEnabled || fetchedUrls.isEmpty()) {
        return copy(items = items.map { it.copy(source = "") })
    }
    return copy(
        items = items.map { item ->
            val normalized = normalizeSourceUrl(item.source)
            if (normalized != null && normalized in fetchedUrls) item else item.copy(source = "")
        },
    )
}

internal fun normalizeSourceUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = runCatching {
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) return@runCatching trimmed
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        "$scheme://$host$portPart$path$query$fragment"
    }.getOrDefault(trimmed)
    val withoutTrailingSlash = if (normalized.endsWith("/")) normalized.dropLast(1) else normalized
    return withoutTrailingSlash.ifBlank { null }
}

private const val MAX_CAUSE_CHAIN_DEPTH = 8

/** Bounded so a self-referencing or cyclic cause chain cannot spin forever. */
internal fun Throwable.causeChain(): List<Throwable> =
    generateSequence(this as Throwable?) { it.cause }.take(MAX_CAUSE_CHAIN_DEPTH).toList()

private fun List<String>.anyContains(vararg needles: String): Boolean =
    any { message -> needles.any { message.contains(it, ignoreCase = true) } }

/**
 * Classifies by walking the whole cause chain: Koog and Ktor wrap HTTP failures, so a rejected key
 * usually sits several causes below the outermost message.
 */
internal fun Throwable.toUserMessage(): String {
    val messages = causeChain().map { it.message.orEmpty() }
    return when {
        messages.anyContains("401", "unauthorized") ->
            "OpenAI rejected the API key. Check the key and try again."

        // "timed out" is the JDK/Ktor socket wording; without it a real read timeout would fall
        // through to the generic branch.
        messages.anyContains("network", "timeout", "timed out") ->
            "Network or OpenAI request failed. Check connectivity and try again."

        messages.anyContains("serial", "schema", "structured") ->
            "Structured output or serialization failed. The model response did not match the DTO contract."

        messages.anyContains("research tool call") ->
            messages.first { it.contains("research tool call", ignoreCase = true) }

        messages.anyContains("tool") ->
            "Koog tool execution failed. The app-owned Brave Search or WebFetch tool could not complete."

        messages.anyContains("Koog", "agent") ->
            "Koog setup or agent execution failed."

        else -> messages.firstOrNull { it.isNotBlank() } ?: "Koog nutrition estimate failed."
    }
}

/** True for rate limits, server errors, and IO timeouts. Never true for a rejected key. */
internal fun Throwable.isRetryableEstimateFailure(): Boolean {
    if (isAuthenticationFailure()) return false
    val chain = causeChain()
    val messages = chain.map { it.message.orEmpty() }
    return messages.anyContains(
        "429",
        "rate limit",
        "rate_limit",
        "too many requests",
        "500",
        "502",
        "503",
        "504",
        "server error",
        "server_error",
        "overloaded",
        "temporarily",
        "timeout",
        "timed out",
        "connection reset",
    ) || chain.any { it is IOException }
}

internal fun Throwable.isAuthenticationFailure(): Boolean =
    causeChain().map { it.message.orEmpty() }
        .anyContains("401", "unauthorized", "invalid_api_key", "invalid api key", "incorrect api key")

internal fun Throwable.toDiagnosticString(): String = buildString {
    appendLine("Diagnostic detail, sanitized; API keys are not logged or included.")
    causeChain().forEachIndexed { index, throwable ->
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
