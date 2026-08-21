package com.example.vocalorie.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.example.vocalorie.settings.ToolSettings
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BraveSearchTool(
    private val settings: ToolSettings,
    private val researchToolCallLimiter: ResearchToolCallLimiter,
    private val fetcher: HttpTextFetcher = KtorHttpTextFetcher.shared,
) : SimpleTool<BraveSearchTool.Args>(
    argsType = typeToken<Args>(),
    name = TOOL_NAME,
    description = "Returns nutrition-related search snippets for a query using the configured Brave Search API.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Search query, for example: Big Mac calories")
        val query: String,
    )

    companion object {
        /** The tool identity as registered with Koog; never surfaced to the user. */
        const val TOOL_NAME: String = "brave_search"
    }

    override suspend fun execute(args: Args): String {
        val apiKey = settings.braveApiKey?.trim().orEmpty()
        require(apiKey.isNotBlank()) { "Brave Search is not configured. Save a Brave API key in Settings." }
        researchToolCallLimiter.acquire(name)
        return realBraveSearch(args.query, apiKey)
    }

    /**
     * Keeps three outcomes apart: a transport failure, a body that cannot be parsed, and a search
     * that genuinely found nothing. Only the last one is allowed to look like an empty result.
     */
    private suspend fun realBraveSearch(query: String, apiKey: String): String {
        val response = try {
            fetcher.fetch(
                HttpTextRequest(
                    url = AgentToolHelpers.braveSearchUrl(query),
                    headers = mapOf(
                        "Accept" to "application/json",
                        "X-Subscription-Token" to apiKey,
                    ),
                    maxBytes = AgentToolHelpers.MAX_SEARCH_RESPONSE_BYTES,
                ),
            )
        } catch (error: ResearchRequestException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw ResearchRequestException(
                "Brave Search request failed: ${error.message ?: error::class.simpleName}",
                error,
            )
        }
        if (!response.isSuccess) {
            throw ResearchRequestException(
                "Brave Search rejected the request (HTTP ${response.status}). " +
                    "This is a grounding failure, not a search that found nothing.",
            )
        }
        if (response.truncated) {
            throw ResearchRequestException(
                "Brave Search returned more than ${AgentToolHelpers.MAX_SEARCH_RESPONSE_BYTES} bytes and was cut off.",
            )
        }
        return AgentToolHelpers.extractBraveSnippets(response.body).ifBlank {
            "Real Brave result: no web snippets returned for '$query'. Preserve uncertainty."
        }
    }
}

class WebFetchTool(
    private val researchToolCallLimiter: ResearchToolCallLimiter,
    private val onFetched: (String) -> Unit = {},
    private val fetcher: HttpTextFetcher = KtorHttpTextFetcher.shared,
) : SimpleTool<WebFetchTool.Args>(
    argsType = typeToken<Args>(),
    name = "web_fetch",
    description = "Fetches page text for a URL using a real HTTP request.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("URL to fetch")
        val url: String,
    )

    override suspend fun execute(args: Args): String {
        researchToolCallLimiter.acquire(name)
        return realWebFetch(args.url)
    }

    private suspend fun realWebFetch(url: String): String {
        AgentToolHelpers.requireSafeFetchUrl(url)
        val response = fetcher.fetch(
            HttpTextRequest(
                url = url,
                // Bounds memory, not excerpt quality: markup is stripped afterwards and the
                // cleaned text is capped at MAX_TOOL_TEXT_CHARS. Reading only 4 KiB of raw HTML
                // would leave a few hundred usable characters and degrade grounding.
                maxBytes = AgentToolHelpers.MAX_FETCH_RESPONSE_BYTES,
                requireTextContent = true,
            ),
        )
        if (!response.isSuccess) {
            // A non-2xx page (e.g. a 404) still returns body text; do NOT let such a URL
            // count as a fetched source. Inform the model so it tries another page.
            return "web_fetch could not retrieve $url (HTTP ${response.status}). Do not cite this URL as a source."
        }
        val text = "Fetched content for $url:\n" + AgentToolHelpers.sanitizeFetchedText(response.body)
        onFetched(url)
        return text
    }
}

class ResearchToolCallLimiter(maxCalls: Int) {
    private val limit = maxCalls.coerceIn(ToolSettings.maxResearchToolCallsRange)
    private val used = AtomicInteger(0)

    fun acquire(toolName: String): Int {
        while (true) {
            val current = used.get()
            if (current >= limit) throw IllegalStateException(limitMessage())
            val next = current + 1
            if (used.compareAndSet(current, next)) return next
        }
    }

    private fun limitMessage(): String = if (limit == 0) {
        "Research tool calls are disabled for this estimate. Increase Max research tool calls in Settings to allow Brave/WebFetch calls."
    } else {
        "Research tool call limit reached (${used.get()}/$limit). Increase Max research tool calls in Settings to allow more Brave/WebFetch calls."
    }
}

object AgentToolHelpers {
    private val braveJson = Json { ignoreUnknownKeys = true }

    fun braveSearchUrl(query: String): String =
        "https://api.search.brave.com/res/v1/web/search?q=${query.urlEncode()}&count=3&text_decorations=false"

    /** Rejects a target the fetch-safety policy does not permit; see [FetchUrlPolicy]. */
    fun requireSafeFetchUrl(url: String) {
        FetchUrlPolicy.rejectionReason(url)?.let { throw IllegalArgumentException(it) }
    }

    /**
     * Returns formatted snippets, or an empty string when the response parsed but held no snippets.
     *
     * @throws ResearchRequestException when the body cannot be parsed into the expected structure,
     * so an unparseable body is never mistaken for a search that found nothing.
     */
    fun extractBraveSnippets(rawJson: String): String {
        val response = try {
            braveJson.decodeFromString(BraveSearchResponse.serializer(), rawJson)
        } catch (error: Exception) {
            throw ResearchRequestException("Brave Search returned a body that could not be parsed.", error)
        }
        return response.web?.results.orEmpty()
            .take(3)
            .joinToString("\n") { result ->
                listOfNotNull(result.title, result.description, result.url)
                    .joinToString(" — ")
            }
            .take(MAX_TOOL_TEXT_CHARS)
            .let { if (it.isBlank()) it else "Real Brave results:\n$it" }
    }

    fun sanitizeFetchedText(text: String): String = text
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_TOOL_TEXT_CHARS)
        .ifBlank { "No readable text extracted." }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    /** Excerpt retained from a fetched page, and the hard bound on how much of it is read. */
    const val MAX_TOOL_TEXT_CHARS = 4_000

    /** Bound on a search response body: large enough to parse, small enough to stay safe. */
    const val MAX_SEARCH_RESPONSE_BYTES = 256 * 1024

    const val MAX_FETCH_RESPONSE_BYTES = 256 * 1024
}

@Serializable
private data class BraveSearchResponse(val web: BraveWebResults? = null)

@Serializable
private data class BraveWebResults(val results: List<BraveWebResult> = emptyList())

@Serializable
private data class BraveWebResult(
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
)

fun vocalorieToolRegistry(
    settings: ToolSettings = ToolSettings(),
    fetcher: HttpTextFetcher = KtorHttpTextFetcher.shared,
    onUrlFetched: (String) -> Unit = {},
): ToolRegistry = ToolRegistry {
    val researchToolCallLimiter = ResearchToolCallLimiter(settings.maxResearchToolCalls)
    tool(BraveSearchTool(settings, researchToolCallLimiter, fetcher))
    tool(WebFetchTool(researchToolCallLimiter, onUrlFetched, fetcher))
}
