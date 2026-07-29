package com.example.vocalorie.tools

/**
 * Injectable HTTP seam for the agent tool layer.
 *
 * Exists so the tool layer can be tested without live network calls, and so one long-lived client
 * is reused instead of building a new engine per tool invocation.
 *
 * Implementations MUST:
 * - read at most [HttpTextRequest.maxBytes] of the response body into memory;
 * - report the transport status rather than throwing on a non-2xx response;
 * - validate every redirect hop against [FetchUrlPolicy] and reject a disallowed hop.
 */
fun interface HttpTextFetcher {
    suspend fun fetch(request: HttpTextRequest): HttpTextResponse
}

data class HttpTextRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** Hard cap on bytes read into memory. The body is truncated at this bound, never read whole. */
    val maxBytes: Int,
    /** When true, the fetcher rejects a target whose content type is not text-like. */
    val requireTextContent: Boolean = false,
)

data class HttpTextResponse(
    val status: Int,
    val contentType: String?,
    val body: String,
    /** The URL actually retrieved, after any permitted redirects. */
    val finalUrl: String,
    /** True when [body] was cut off at [HttpTextRequest.maxBytes]. */
    val truncated: Boolean = false,
) {
    val isSuccess: Boolean get() = status in 200..299
}

/** Raised when a fetch target — original or a redirect hop — is not permitted. */
class FetchNotPermittedException(message: String) : Exception(message)

/** Raised when a research request fails at the transport level, distinct from an empty result. */
class ResearchRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)
