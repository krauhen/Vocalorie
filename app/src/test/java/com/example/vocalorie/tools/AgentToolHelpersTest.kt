package com.example.vocalorie.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolHelpersTest {
    @Test
    fun braveSearchUrlEncodesQuery() {
        assertEquals(
            "https://api.search.brave.com/res/v1/web/search?q=big+mac+calories&count=3&text_decorations=false",
            AgentToolHelpers.braveSearchUrl("big mac calories"),
        )
    }

    @Test
    fun extractsBraveSnippetsFromJson() {
        val raw = """
            {"web":{"results":[{"title":"Egg nutrition","url":"https://example.test/egg","description":"One egg has about 70 kcal."}]}}
        """.trimIndent()

        val snippets = AgentToolHelpers.extractBraveSnippets(raw)

        assertTrue(snippets.contains("Real Brave results"))
        assertTrue(snippets.contains("Egg nutrition"))
        assertTrue(snippets.contains("70 kcal"))
    }

    @Test
    fun sanitizeFetchedTextRemovesBasicHtmlNoise() {
        assertEquals(
            "Food calories",
            AgentToolHelpers.sanitizeFetchedText("<html><script>bad()</script><body>Food <b>calories</b></body></html>"),
        )
    }

    @Test
    fun safeFetchUrlRejectsLocalHosts() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentToolHelpers.requireSafeFetchUrl("http://localhost/secret")
        }
    }

    @Test
    fun researchToolLimiterAllowsOnlyConfiguredCallsAcrossTools() {
        val limiter = ResearchToolCallLimiter(maxCalls = 2)

        assertEquals(1, limiter.acquire("brave_search"))
        assertEquals(2, limiter.acquire("web_fetch"))

        val error = assertThrows(IllegalStateException::class.java) {
            limiter.acquire("brave_search")
        }
        assertEquals(
            "Research tool call limit reached (2/2). Increase Max research tool calls in Settings to allow more Brave/WebFetch calls.",
            error.message,
        )
    }

    @Test
    fun researchToolLimiterSupportsZeroCalls() {
        val limiter = ResearchToolCallLimiter(maxCalls = 0)

        val error = assertThrows(IllegalStateException::class.java) {
            limiter.acquire("web_fetch")
        }
        assertEquals(
            "Research tool calls are disabled for this estimate. Increase Max research tool calls in Settings to allow Brave/WebFetch calls.",
            error.message,
        )
    }
}
