package com.example.vocalorie.tools

import com.example.vocalorie.settings.ToolSettings
import io.ktor.utils.io.ByteReadChannel
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the tool layer through the injected [HttpTextFetcher] seam: no HTTP engine is built and
 * no network call is made.
 *
 * URLs use public IP literals so the fetch-safety policy resolves them without a name-service
 * lookup — [InetAddress.getAllByName] parses a literal address directly. 203.0.113.0/24 is the
 * reserved TEST-NET-3 range, and 169.254.0.0/16 is link-local.
 */
class AgentToolFetchSeamTest {

    private val publicUrl = "https://203.0.113.10/egg-nutrition"
    private val linkLocalUrl = "http://169.254.169.254/latest/meta-data"

    private fun response(
        url: String,
        status: Int,
        body: String,
        contentType: String? = "text/html",
        truncated: Boolean = false,
    ) = HttpTextResponse(
        status = status,
        contentType = contentType,
        body = body,
        finalUrl = url,
        truncated = truncated,
    )

    private fun braveTool(fetcher: HttpTextFetcher) = BraveSearchTool(
        settings = ToolSettings(braveApiKey = "test-key"),
        researchToolCallLimiter = ResearchToolCallLimiter(maxCalls = 4),
        fetcher = fetcher,
    )

    @Test
    fun braveSearchReportsRejectedStatusAsFailureNotAnEmptyResult() {
        val stubFetcher = HttpTextFetcher { request ->
            response(request.url, 401, """{"error":"unauthorized"}""", "application/json")
        }

        val error = assertThrows(ResearchRequestException::class.java) {
            runBlocking { braveTool(stubFetcher).execute(BraveSearchTool.Args("big mac calories")) }
        }

        assertTrue(error.message!!.contains("401"))
        assertFalse(error.message!!.contains("no web snippets returned"))
    }

    @Test
    fun braveSearchReportsRateLimitedStatusAsFailure() {
        val stubFetcher = HttpTextFetcher { request ->
            response(request.url, 429, "slow down", "text/plain")
        }

        val error = assertThrows(ResearchRequestException::class.java) {
            runBlocking { braveTool(stubFetcher).execute(BraveSearchTool.Args("egg calories")) }
        }

        assertTrue(error.message!!.contains("429"))
    }

    @Test
    fun braveSearchReportsAnUnparseableSuccessfulBodyAsFailure() {
        val stubFetcher = HttpTextFetcher { request ->
            response(request.url, 200, "<html>not the expected json</html>")
        }

        val error = assertThrows(ResearchRequestException::class.java) {
            runBlocking { braveTool(stubFetcher).execute(BraveSearchTool.Args("egg calories")) }
        }

        assertTrue(error.message!!.contains("could not be parsed"))
        assertFalse(error.message!!.contains("no web snippets returned"))
    }

    @Test
    fun braveSearchStillReportsAGenuinelyEmptyResultAsEmpty() {
        val stubFetcher = HttpTextFetcher { request ->
            response(request.url, 200, """{"web":{"results":[]}}""", "application/json")
        }

        val result = runBlocking { braveTool(stubFetcher).execute(BraveSearchTool.Args("egg calories")) }

        assertEquals(
            "Real Brave result: no web snippets returned for 'egg calories'. Preserve uncertainty.",
            result,
        )
    }

    @Test
    fun braveSearchSendsTheSubscriptionTokenAndABoundedByteLimit() {
        val requests = mutableListOf<HttpTextRequest>()
        val stubFetcher = HttpTextFetcher { request ->
            requests += request
            response(request.url, 200, """{"web":{"results":[]}}""", "application/json")
        }

        runBlocking { braveTool(stubFetcher).execute(BraveSearchTool.Args("egg calories")) }

        val sent = requests.single()
        assertEquals("test-key", sent.headers["X-Subscription-Token"])
        assertEquals(AgentToolHelpers.MAX_SEARCH_RESPONSE_BYTES, sent.maxBytes)
    }

    @Test
    fun webFetchRequestsABoundedTextOnlyReadAndRecordsTheUrl() {
        val requests = mutableListOf<HttpTextRequest>()
        val fetchedUrls = mutableListOf<String>()
        val recordingFetcher = HttpTextFetcher { request ->
            requests += request
            response(request.url, 200, "<html><body>One egg has about 70 kcal</body></html>")
        }
        val tool = WebFetchTool(
            researchToolCallLimiter = ResearchToolCallLimiter(maxCalls = 2),
            onFetched = { fetchedUrls += it },
            fetcher = recordingFetcher,
        )

        val result = runBlocking { tool.execute(WebFetchTool.Args(publicUrl)) }

        val sent = requests.single()
        assertEquals(AgentToolHelpers.MAX_FETCH_RESPONSE_BYTES, sent.maxBytes)
        assertTrue(sent.requireTextContent)
        assertTrue(result.contains("One egg has about 70 kcal"))
        assertEquals(listOf(publicUrl), fetchedUrls)
    }

    @Test
    fun webFetchDoesNotRecordAUrlThatReturnedANonSuccessStatus() {
        val fetchedUrls = mutableListOf<String>()
        val stubFetcher = HttpTextFetcher { request -> response(request.url, 404, "not found") }
        val tool = WebFetchTool(
            researchToolCallLimiter = ResearchToolCallLimiter(maxCalls = 2),
            onFetched = { fetchedUrls += it },
            fetcher = stubFetcher,
        )

        val result = runBlocking { tool.execute(WebFetchTool.Args(publicUrl)) }

        assertEquals(
            "web_fetch could not retrieve $publicUrl (HTTP 404). Do not cite this URL as a source.",
            result,
        )
        assertTrue(fetchedUrls.isEmpty())
    }

    @Test
    fun redirectToALinkLocalAddressIsRejectedBeforeTheHopIsRetrieved() {
        val visited = mutableListOf<String>()

        val error = assertThrows(FetchNotPermittedException::class.java) {
            runBlocking {
                followValidatedRedirects(startUrl = publicUrl, resolver = literalResolver) { url ->
                    visited += url
                    if (url == publicUrl) {
                        FetchHop.Redirect(linkLocalUrl)
                    } else {
                        FetchHop.Final(response(url, 200, "instance credentials", "text/plain"))
                    }
                }
            }
        }

        assertEquals(listOf(publicUrl), visited)
        assertTrue(error.message!!.contains("local or private network addresses"))
    }

    @Test
    fun unresolvableHostIsRejectedInsteadOfVacuouslyPassing() {
        var attempts = 0

        val error = assertThrows(FetchNotPermittedException::class.java) {
            runBlocking {
                followValidatedRedirects(
                    startUrl = "https://nutrition.invalid/egg",
                    resolver = FetchUrlPolicy.HostResolver { null },
                ) { url ->
                    attempts++
                    FetchHop.Final(response(url, 200, "body", "text/plain"))
                }
            }
        }

        assertEquals(0, attempts)
        assertTrue(error.message!!.contains("cannot resolve"))
    }

    @Test
    fun policyRejectsAHostWhoseAddressesCannotBeResolved() {
        assertTrue(
            FetchUrlPolicy.rejectionReason(
                "https://nutrition.invalid/egg",
                resolver = FetchUrlPolicy.HostResolver { null },
            )!!.contains("cannot resolve"),
        )
        assertTrue(
            FetchUrlPolicy.rejectionReason(
                "https://nutrition.invalid/egg",
                resolver = FetchUrlPolicy.HostResolver { emptyList() },
            )!!.contains("cannot resolve"),
        )
    }

    @Test
    fun aPermittedRedirectChainIsFollowedAndReportsTheFinalUrl() {
        val visited = mutableListOf<String>()
        val secondUrl = "https://203.0.113.20/egg"

        val result = runBlocking {
            followValidatedRedirects(startUrl = publicUrl, resolver = literalResolver) { url ->
                visited += url
                if (url == publicUrl) {
                    FetchHop.Redirect(secondUrl)
                } else {
                    FetchHop.Final(response(url, 200, "70 kcal", "text/plain"))
                }
            }
        }

        assertEquals(listOf(publicUrl, secondUrl), visited)
        assertEquals(secondUrl, result.finalUrl)
    }

    @Test
    fun aRelativeRedirectIsResolvedAgainstTheUrlThatIssuedIt() {
        val visited = mutableListOf<String>()

        val result = runBlocking {
            followValidatedRedirects(startUrl = publicUrl, resolver = literalResolver) { url ->
                visited += url
                if (url == publicUrl) FetchHop.Redirect("/nutrition/egg") else FetchHop.Final(
                    response(url, 200, "70 kcal", "text/plain"),
                )
            }
        }

        assertEquals("https://203.0.113.10/nutrition/egg", result.finalUrl)
        assertEquals(listOf(publicUrl, "https://203.0.113.10/nutrition/egg"), visited)
    }

    @Test
    fun anEndlessRedirectChainIsBounded() {
        var attempts = 0

        val error = assertThrows(FetchNotPermittedException::class.java) {
            runBlocking {
                followValidatedRedirects(
                    startUrl = publicUrl,
                    maxRedirects = 2,
                    resolver = literalResolver,
                ) { _ ->
                    attempts++
                    FetchHop.Redirect(publicUrl)
                }
            }
        }

        assertEquals(3, attempts)
        assertTrue(error.message!!.contains("stopped after 2 redirects"))
    }

    @Test
    fun anOversizedBodyIsOnlyReadUpToTheBound() = runBlocking {
        val oversized = "x".repeat(50_000)

        val bounded = readBoundedText(ByteReadChannel(oversized), maxBytes = 4_000)

        assertEquals(4_000, bounded.text.length)
        assertTrue(bounded.truncated)
    }

    @Test
    fun aBodyUnderTheBoundIsReadWholeAndNotMarkedTruncated() = runBlocking {
        val bounded = readBoundedText(ByteReadChannel("One egg has about 70 kcal"), maxBytes = 4_000)

        assertEquals("One egg has about 70 kcal", bounded.text)
        assertFalse(bounded.truncated)
    }

    @Test
    fun onlyTextLikeContentTypesAreAccepted() {
        assertTrue(isTextLikeContentType("text/html; charset=utf-8"))
        assertTrue(isTextLikeContentType("application/json"))
        assertTrue(isTextLikeContentType("application/xhtml+xml"))
        assertFalse(isTextLikeContentType("application/pdf"))
        assertFalse(isTextLikeContentType("image/png"))
        assertFalse(isTextLikeContentType("video/mp4"))
        assertFalse(isTextLikeContentType(null))
    }

    /** Resolves only IP literals, so the policy never reaches a name service during these tests. */
    private val literalResolver = FetchUrlPolicy.HostResolver { host ->
        runCatching { InetAddress.getAllByName(host).toList() }.getOrNull()
    }
}
