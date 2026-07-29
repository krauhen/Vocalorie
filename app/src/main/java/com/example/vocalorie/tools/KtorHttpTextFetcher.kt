package com.example.vocalorie.tools

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable

/**
 * [HttpTextFetcher] backed by one long-lived Ktor client.
 *
 * The engine is built once per process instead of per tool invocation, response bodies are read only
 * up to [HttpTextRequest.maxBytes], and every hop of a redirect chain is validated through
 * [FetchUrlPolicy] before it is retrieved.
 */
class KtorHttpTextFetcher(
    private val httpClient: () -> HttpClient = { sharedAndroidClient },
    private val hostResolver: FetchUrlPolicy.HostResolver = FetchUrlPolicy.SystemHostResolver,
    private val maxRedirects: Int = FetchUrlPolicy.MAX_REDIRECTS,
) : HttpTextFetcher {

    override suspend fun fetch(request: HttpTextRequest): HttpTextResponse =
        followValidatedRedirects(request.url, maxRedirects, hostResolver) { url -> hop(url, request) }

    private suspend fun hop(url: String, request: HttpTextRequest): FetchHop =
        httpClient().prepareGet(url) {
            request.headers.forEach { (name, value) -> header(name, value) }
        }.execute { response -> readHop(url, request, response) }

    private suspend fun readHop(url: String, request: HttpTextRequest, response: HttpResponse): FetchHop {
        val status = response.status.value
        val location = response.headers[HttpHeaders.Location]
        if (status in REDIRECT_STATUSES && !location.isNullOrBlank()) {
            // Body intentionally left unread: the hop target still has to clear FetchUrlPolicy.
            return FetchHop.Redirect(location)
        }
        val contentType = response.headers[HttpHeaders.ContentType]
        if (request.requireTextContent && response.status.isSuccess() && !isTextLikeContentType(contentType)) {
            throw FetchNotPermittedException(
                "web_fetch cannot read ${contentType ?: "an unknown content type"} at $url; only text pages are fetched.",
            )
        }
        val bounded = readBoundedText(response.bodyAsChannel(), request.maxBytes)
        return FetchHop.Final(
            HttpTextResponse(
                status = status,
                contentType = contentType,
                body = bounded.text,
                finalUrl = url,
                truncated = bounded.truncated,
            ),
        )
    }

    companion object {
        /** Process-wide fetcher. The underlying engine is created lazily, on first real use. */
        val shared: HttpTextFetcher by lazy { KtorHttpTextFetcher() }

        private val sharedAndroidClient: HttpClient by lazy {
            HttpClient(Android) {
                // Redirects are followed manually so each hop passes through FetchUrlPolicy.
                followRedirects = false
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 15_000
                }
            }
        }

        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}

internal data class BoundedText(val text: String, val truncated: Boolean)

/**
 * Reads at most [maxBytes] from [channel] and abandons the rest, so a model-chosen target can never
 * pull an arbitrarily large page into memory just to retain a short excerpt of it.
 */
internal suspend fun readBoundedText(channel: ByteReadChannel, maxBytes: Int): BoundedText {
    val limit = maxBytes.coerceAtLeast(1)
    val buffer = ByteArray(limit)
    var read = 0
    while (read < limit) {
        val count = channel.readAvailable(buffer, read, limit - read)
        if (count <= 0) break
        read += count
    }
    val truncated = read >= limit && !channel.isClosedForRead
    channel.cancel()
    return BoundedText(String(buffer, 0, read, Charsets.UTF_8), truncated)
}

private val TEXT_LIKE_CONTENT_TYPES = setOf(
    "application/json",
    "application/xhtml+xml",
    "application/xml",
)

/** True for any `text` subtype, JSON, and XML payloads. An absent or unknown type fails closed. */
internal fun isTextLikeContentType(contentType: String?): Boolean {
    val value = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
    return value.startsWith("text/") ||
        value in TEXT_LIKE_CONTENT_TYPES ||
        value.endsWith("+json") ||
        value.endsWith("+xml")
}

/** One step of a fetch: either a redirect to follow or the response to return. */
internal sealed interface FetchHop {
    data class Redirect(val location: String) : FetchHop
    data class Final(val response: HttpTextResponse) : FetchHop
}

/**
 * Walks a redirect chain, validating every URL through [FetchUrlPolicy] *before* [hop] retrieves it.
 *
 * Kept free of any HTTP engine so the hop-validation rule is testable with a plain lambda.
 */
internal suspend fun followValidatedRedirects(
    startUrl: String,
    maxRedirects: Int = FetchUrlPolicy.MAX_REDIRECTS,
    resolver: FetchUrlPolicy.HostResolver = FetchUrlPolicy.SystemHostResolver,
    hop: suspend (String) -> FetchHop,
): HttpTextResponse {
    var url = startUrl
    var hopsFollowed = 0
    while (true) {
        FetchUrlPolicy.requirePermitted(url, resolver)
        when (val outcome = hop(url)) {
            is FetchHop.Final -> return outcome.response
            is FetchHop.Redirect -> {
                if (hopsFollowed >= maxRedirects) {
                    throw FetchNotPermittedException(
                        "web_fetch stopped after $maxRedirects redirects starting at $startUrl",
                    )
                }
                url = FetchUrlPolicy.resolveRedirect(url, outcome.location)
                    ?: throw FetchNotPermittedException("web_fetch cannot follow an invalid redirect from $url")
                hopsFollowed++
            }
        }
    }
}
