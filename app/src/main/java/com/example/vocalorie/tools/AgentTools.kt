package com.example.vocalorie.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.example.vocalorie.settings.ToolSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

class BraveSearchTool(
    private val settings: ToolSettings,
    private val researchToolCallLimiter: ResearchToolCallLimiter,
) : SimpleTool<BraveSearchTool.Args>(
    argsType = typeToken<Args>(),
    name = "brave_search",
    description = "Returns nutrition-related search snippets for a query using the configured Brave Search API.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Search query, for example: Big Mac calories")
        val query: String,
    )

    override suspend fun execute(args: Args): String {
        val apiKey = settings.braveApiKey?.trim().orEmpty()
        require(apiKey.isNotBlank()) { "Brave Search is not configured. Save a Brave API key in Settings." }
        researchToolCallLimiter.acquire(name)
        return realBraveSearch(args.query, apiKey)
    }

    private suspend fun realBraveSearch(query: String, apiKey: String): String {
        val client = AgentToolHelpers.httpClient()
        return try {
            val raw = client.get(AgentToolHelpers.braveSearchUrl(query)) {
                header("Accept", "application/json")
                header("X-Subscription-Token", apiKey)
            }.bodyAsText()
            AgentToolHelpers.extractBraveSnippets(raw).ifBlank {
                "Real Brave result: no web snippets returned for '$query'. Preserve uncertainty."
            }
        } finally {
            client.close()
        }
    }
}

class WebFetchTool(
    private val researchToolCallLimiter: ResearchToolCallLimiter,
    private val onFetched: (String) -> Unit = {},
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
        val client = AgentToolHelpers.httpClient()
        return try {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                // A non-2xx page (e.g. a 404) still returns body text; do NOT let such a URL
                // count as a fetched source. Inform the model so it tries another page.
                return "web_fetch could not retrieve $url (HTTP ${response.status.value}). Do not cite this URL as a source."
            }
            val text = "Fetched content for $url:\n" + AgentToolHelpers.sanitizeFetchedText(response.bodyAsText())
            onFetched(url)
            text
        } finally {
            client.close()
        }
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

    fun httpClient(): HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    fun requireSafeFetchUrl(url: String) {
        val uri = runCatching { URI(url.trim()) }.getOrElse { throw IllegalArgumentException("web_fetch URL is invalid") }
        require(uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) {
            "web_fetch URL must start with http:// or https://"
        }
        val host = uri.host?.lowercase().orEmpty()
        require(host.isNotBlank()) { "web_fetch URL must include a host" }
        require(host != "localhost" && !host.endsWith(".local")) { "web_fetch cannot fetch local hosts" }
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
        require(addresses.none { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress }) {
            "web_fetch cannot fetch local or private network addresses"
        }
    }

    fun extractBraveSnippets(rawJson: String): String = runCatching {
        val response = braveJson.decodeFromString(BraveSearchResponse.serializer(), rawJson)
        response.web?.results.orEmpty()
            .take(3)
            .joinToString("\n") { result ->
                listOfNotNull(result.title, result.description, result.url)
                    .joinToString(" — ")
            }
            .take(MAX_TOOL_TEXT_CHARS)
            .let { if (it.isBlank()) it else "Real Brave results:\n$it" }
    }.getOrDefault("")

    fun sanitizeFetchedText(text: String): String = text
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_TOOL_TEXT_CHARS)
        .ifBlank { "No readable text extracted." }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private const val MAX_TOOL_TEXT_CHARS = 4_000
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
    onUrlFetched: (String) -> Unit = {},
): ToolRegistry = ToolRegistry {
    val researchToolCallLimiter = ResearchToolCallLimiter(settings.maxResearchToolCalls)
    tool(BraveSearchTool(settings, researchToolCallLimiter))
    tool(WebFetchTool(researchToolCallLimiter, onUrlFetched))
}
