package com.example.vocalorie.ai

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.FoodItemEstimate
import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.model.NutritionAgentResult
import com.example.vocalorie.model.NutritionTotals
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the estimate-path hardening: cause-chain failure classification, bounded retry that never
 * retries a rejected key, per-key executor reuse, race-free fetched-URL recording, and a grounding
 * failure that reaches the caller instead of being absorbed.
 */
class NutritionEstimateReliabilityTest {

    // --- failure classification walks the whole cause chain ---

    @Test
    fun rejectedKeyNestedSeveralCausesDeepStillReadsAsRejectedKey() {
        val failure = RuntimeException(
            "Koog agent run failed",
            IllegalStateException(
                "prompt executor could not complete",
                RuntimeException(
                    "OpenAI request failed",
                    IOException("HTTP 401 Unauthorized: invalid_api_key"),
                ),
            ),
        )

        assertEquals("OpenAI rejected the API key. Check the key and try again.", failure.toUserMessage())
    }

    @Test
    fun outermostWrapperDoesNotHideANestedSchemaFailure() {
        val failure = RuntimeException(
            "agent step failed",
            IllegalStateException("structured output did not match the schema"),
        )

        assertEquals(
            "Structured output or serialization failed. The model response did not match the DTO contract.",
            failure.toUserMessage(),
        )
    }

    @Test
    fun researchToolCallMessageIsPassedThroughFromDeepInTheChain() {
        val failure = RuntimeException("agent failed", IllegalStateException("Research tool call budget exhausted."))

        assertEquals("Research tool call budget exhausted.", failure.toUserMessage())
    }

    @Test
    fun blankMessagesFallBackToTheGenericEstimateFailureText() {
        val failure = RuntimeException(null as String?, RuntimeException(""))

        assertEquals("Koog nutrition estimate failed.", failure.toUserMessage())
    }

    @Test
    fun validationFailuresKeepTheirOwnWording() {
        assertEquals("Enter an OpenAI API key.", IllegalArgumentException("Enter an OpenAI API key.").toUserMessage())
        assertEquals("Enter a nutrition query.", IllegalArgumentException("Enter a nutrition query.").toUserMessage())
    }

    @Test
    fun causeChainTraversalIsBoundedForASelfReferencingCause() {
        val failure = SelfCausedException("loops forever")

        assertEquals(8, failure.causeChain().size)
    }

    // --- bounded retry ---

    @Test
    fun aWrappedAuthenticationFailureIsNeverRetried() {
        val attempts = AtomicInteger(0)
        val backoffs = mutableListOf<Long>()

        val thrown = runCatching {
            runBlocking {
                withBoundedRetry<Unit>(awaitBackoff = { backoffs += it }) {
                    attempts.incrementAndGet()
                    throw RuntimeException("agent failed", IllegalStateException("401 unauthorized"))
                }
            }
        }.exceptionOrNull()

        assertNotNull(thrown)
        assertEquals(1, attempts.get())
        assertTrue(backoffs.isEmpty())
    }

    @Test
    fun aTransientServerFailureIsRetriedUpToTheBoundThenReported() {
        val attempts = AtomicInteger(0)
        val backoffs = mutableListOf<Long>()

        val thrown = runCatching {
            runBlocking {
                withBoundedRetry<Unit>(awaitBackoff = { backoffs += it }) {
                    attempts.incrementAndGet()
                    throw IllegalStateException("OpenAI responded 503 Service Unavailable")
                }
            }
        }.exceptionOrNull()

        assertNotNull(thrown)
        assertEquals(KoogNutritionAgent.ESTIMATE_MAX_ATTEMPTS, attempts.get())
        assertEquals(KoogNutritionAgent.ESTIMATE_MAX_ATTEMPTS - 1, backoffs.size)
        assertTrue("backoff must grow: $backoffs", backoffs == backoffs.sorted() && backoffs.first() > 0)
    }

    @Test
    fun aRateLimitedAttemptRecoversOnTheNextTry() = runBlocking {
        val attempts = AtomicInteger(0)

        val value = withBoundedRetry(awaitBackoff = {}) { attempt ->
            attempts.incrementAndGet()
            if (attempt == 1) throw IllegalStateException("429 rate limit exceeded") else "estimate"
        }

        assertEquals("estimate", value)
        assertEquals(2, attempts.get())
    }

    @Test
    fun anIoFailureBuriedInTheChainIsTransient() {
        val failure = RuntimeException("estimate failed", SocketTimeoutException("read timed out"))

        assertTrue(failure.isRetryableEstimateFailure())
    }

    @Test
    fun aNestedNetworkTimeoutReadsAsANetworkFailure() {
        val failure = RuntimeException("estimate failed", IOException("Request timeout has expired"))

        assertTrue(failure.isRetryableEstimateFailure())
        assertEquals("Network or OpenAI request failed. Check connectivity and try again.", failure.toUserMessage())
    }

    @Test
    fun theRequestTimeoutIsTransientAndBoundedWellBelowTheClientDefault() {
        val failure = NutritionEstimateTimeoutException(KoogNutritionAgent.ESTIMATE_REQUEST_TIMEOUT_MS)

        assertTrue(failure.isRetryableEstimateFailure())
        assertEquals("Network or OpenAI request failed. Check connectivity and try again.", failure.toUserMessage())
        assertTrue(KoogNutritionAgent.ESTIMATE_REQUEST_TIMEOUT_MS in 1L until 900_000L)
    }

    @Test
    fun aStructuredOutputFailureIsNotRetried() {
        val failure = IllegalStateException("serialization failed for the nutrition schema")

        assertFalse(failure.isRetryableEstimateFailure())
    }

    // --- one executor per key, reused and superseded ---

    @Test
    fun theSameKeyReusesOneResourceAndAChangedKeyDisposesTheOldOne() {
        val created = mutableListOf<String>()
        val disposed = mutableListOf<String>()
        val cache = KeyedResourceCache(
            create = { key -> Resource(key).also { created += key } },
            dispose = { disposed += it.key },
        )

        val first = cache.get("sk-one")
        val second = cache.get("sk-one")
        val third = cache.get("sk-two")

        assertSame(first, second)
        assertEquals(listOf("sk-one", "sk-two"), created)
        assertEquals(listOf("sk-one"), disposed)
        assertEquals("sk-two", third.key)
        assertSame(third, cache.get("sk-two"))
    }

    @Test
    fun concurrentLookupsForOneKeyBuildTheResourceOnlyOnce() {
        val creations = AtomicInteger(0)
        val cache = KeyedResourceCache(
            create = { key -> Resource(key).also { creations.incrementAndGet() } },
        )
        val threadCount = 8
        val barrier = CyclicBarrier(threadCount)
        val done = CountDownLatch(threadCount)
        val seen = java.util.Collections.synchronizedList(mutableListOf<Resource>())

        repeat(threadCount) {
            Thread {
                barrier.await()
                seen += cache.get("sk-shared")
                done.countDown()
            }.start()
        }

        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(1, creations.get())
        assertEquals(1, seen.distinct().size)
    }

    @Test
    fun aFailedRebuildLeavesThePreviousResourceUsable() {
        val cache = KeyedResourceCache<Resource>(
            create = { key -> if (key == "bad") throw IllegalStateException("cannot build") else Resource(key) },
        )

        val good = cache.get("good")
        assertNotNull(runCatching { cache.get("bad") }.exceptionOrNull())
        assertSame(good, cache.get("good"))
    }

    // --- fetched URLs survive concurrency ---

    @Test
    fun everyConcurrentlyFetchedUrlIsRecordedAndKeepsItsItemSource() {
        val fetchedUrls = newFetchedUrlSet()
        val urls = (1..64).map { "https://example.org/food/$it" }
        val barrier = CyclicBarrier(urls.size)
        val done = CountDownLatch(urls.size)

        urls.forEach { url ->
            Thread {
                barrier.await()
                normalizeSourceUrl("$url/")?.let { fetchedUrls.add(it) }
                done.countDown()
            }.start()
        }

        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(urls.size, fetchedUrls.size)

        val outcome = buildEstimateOutcome(
            result = resultWithSources(urls),
            fetchedUrls = fetchedUrls,
            groundingEnabled = true,
            groundingFailure = null,
        )

        assertEquals(urls, outcome.result.items.map { it.source })
        assertNull(outcome.groundingFailureMessage)
    }

    @Test
    fun anUnfetchedSourceIsStillBlankedWhileFetchedOnesSurvive() {
        val fetchedUrls = newFetchedUrlSet()
        fetchedUrls.add("https://example.org/food/1")

        val outcome = buildEstimateOutcome(
            result = resultWithSources(listOf("https://example.org/food/1", "https://guessed.example/food/9")),
            fetchedUrls = fetchedUrls,
            groundingEnabled = true,
            groundingFailure = null,
        )

        assertEquals(listOf("https://example.org/food/1", ""), outcome.result.items.map { it.source })
    }

    // --- a grounding failure is reported, not absorbed ---

    @Test
    fun aFailedGroundingPassSurfacesAWarningAndKeepsItsCause() {
        val failure = IllegalStateException(
            "brave search failed",
            IOException("HTTP 401 Unauthorized for key sk-braveSecretValue"),
        )

        val outcome = buildEstimateOutcome(
            result = resultWithSources(listOf("https://example.org/food/1")),
            fetchedUrls = emptySet(),
            groundingEnabled = true,
            groundingFailure = failure,
        )

        assertEquals(KoogNutritionAgent.GROUNDING_FAILURE_MESSAGE, outcome.groundingFailureMessage)
        val diagnostic = outcome.groundingFailureDiagnostic
        assertNotNull(diagnostic)
        assertTrue(diagnostic!!.contains("IllegalStateException"))
        assertTrue(diagnostic.contains("IOException"))
        assertTrue(diagnostic.contains("HTTP 401 Unauthorized"))
        assertFalse("the key must never reach the diagnostic", diagnostic.contains("sk-braveSecretValue"))
        assertEquals(listOf(""), outcome.result.items.map { it.source })
    }

    @Test
    fun aFailedGroundingPassIsDistinguishableFromAPassThatFoundNothing() {
        val foundNothing = buildEstimateOutcome(
            result = resultWithSources(listOf("https://example.org/food/1")),
            fetchedUrls = emptySet(),
            groundingEnabled = true,
            groundingFailure = null,
        )
        val failed = buildEstimateOutcome(
            result = resultWithSources(listOf("https://example.org/food/1")),
            fetchedUrls = emptySet(),
            groundingEnabled = true,
            groundingFailure = IllegalStateException("grounding failed"),
        )

        assertNull(foundNothing.groundingFailureMessage)
        assertNull(foundNothing.groundingFailureDiagnostic)
        assertNotNull(failed.groundingFailureMessage)
        assertEquals(listOf(""), foundNothing.result.items.map { it.source })
        assertEquals(listOf(""), failed.result.items.map { it.source })
    }

    @Test
    fun aGroundingFailureIsNotReportedAsARejectedOpenAiKey() {
        val outcome = buildEstimateOutcome(
            result = resultWithSources(listOf("https://example.org/food/1")),
            fetchedUrls = emptySet(),
            groundingEnabled = true,
            groundingFailure = IOException("Brave search rejected the key with 401"),
        )

        assertEquals(KoogNutritionAgent.GROUNDING_FAILURE_MESSAGE, outcome.groundingFailureMessage)
        assertFalse(outcome.groundingFailureMessage!!.contains("OpenAI"))
    }

    @Test
    fun groundingThatWasNeverAttemptedBlanksSourcesWithoutAWarning() {
        val outcome = buildEstimateOutcome(
            result = resultWithSources(listOf("https://example.org/food/1")),
            fetchedUrls = newFetchedUrlSet().apply { add("https://example.org/food/1") },
            groundingEnabled = false,
            groundingFailure = null,
        )

        assertEquals(listOf(""), outcome.result.items.map { it.source })
        assertNull(outcome.groundingFailureMessage)
    }

    private fun resultWithSources(sources: List<String>): NutritionAgentResult = NutritionAgentResult(
        query = "test meal",
        title = "Testmahlzeit",
        items = sources.mapIndexed { index, source ->
            FoodItemEstimate(
                name = "item $index",
                quantity = "100 g",
                amountGml = 100.0,
                caloriesKcal = 100.0,
                proteinG = 1.0,
                carbsG = 1.0,
                fatG = 1.0,
                saturatedFatG = 0.0,
                sugarG = 0.0,
                saltG = 0.0,
                source = source,
                reasoning = "",
            )
        },
        totals = NutritionTotals(
            caloriesKcal = 100.0,
            amountGml = 100.0,
            proteinG = 1.0,
            carbsG = 1.0,
            fatG = 1.0,
            saturatedFatG = 0.0,
            sugarG = 0.0,
            saltG = 0.0,
        ),
        assumptions = emptyList(),
        warnings = emptyList(),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = true,
        category = MealCategory.MEAL,
    )

    private class Resource(val key: String)

    private class SelfCausedException(message: String) : Exception(message) {
        override val cause: Throwable? get() = this
    }
}
