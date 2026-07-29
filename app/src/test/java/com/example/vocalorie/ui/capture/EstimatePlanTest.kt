package com.example.vocalorie.ui.capture

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import com.example.vocalorie.data.CachedMealMatch
import com.example.vocalorie.data.findCachedMealMatch
import com.example.vocalorie.data.toCachedMealEntity
import com.example.vocalorie.data.toCachedMealMatch
import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the prompt-construction and cache-branch rule extracted out of the Estimate lambda: what
 * the model is asked, what the reviewed draft is labelled with, and which of the three outcomes a
 * tap produces.
 */
class EstimatePlanTest {

    private val missingInputMessage = "Enter a nutrition query or attach a photo."

    private fun attachment(label: String): GalleryImageAttachment = GalleryImageAttachment(
        label = label,
        image = AttachmentSource.Image(
            content = AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3)),
            format = "jpg",
            mimeType = "image/jpeg",
            fileName = label,
        ),
    )

    private fun cachedMatch(query: String): CachedMealMatch =
        cachedDraft(query).toCachedMealEntity()!!.toCachedMealMatch(query)

    private fun cachedDraft(query: String): EditableMealDraft = EditableMealDraft(
        title = "",
        query = query,
        items = listOf(
            EditableFoodItem(
                name = query,
                quantity = query,
                amountGml = "100",
                caloriesKcal = "10",
                proteinG = "",
                carbsG = "",
                fatG = "",
                saturatedFatG = "",
                sugarG = "",
                saltG = "",
                source = "",
                reasoning = "",
            ),
        ),
        caloriesKcal = "",
        amountGml = "",
        proteinG = "",
        carbsG = "",
        fatG = "",
        saturatedFatG = "",
        sugarG = "",
        saltG = "",
        assumptionsText = "",
        warningsText = "",
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = false,
    )

    private fun runEstimate(plan: EstimatePlan): EstimateRequest {
        assertTrue("expected an estimate to run, got $plan", plan is EstimatePlan.RunEstimate)
        return (plan as EstimatePlan.RunEstimate).request
    }

    private fun servedFromCache(plan: EstimatePlan): EstimatePlan.ServeFromCache {
        assertTrue("expected the cache branch, got $plan", plan is EstimatePlan.ServeFromCache)
        return plan as EstimatePlan.ServeFromCache
    }

    // --- Prompt construction ---

    @Test
    fun aTypedQueryIsSentAndLabelledVerbatim() {
        assertEquals("Buttermilch 200g", estimateRequestQuery("Buttermilch 200g", emptyList()))
        assertEquals("Buttermilch 200g", estimateDraftQuery("Buttermilch 200g", emptyList()))
    }

    @Test
    fun aTypedQueryWinsOverAnyNumberOfPhotos() {
        val images = listOf(attachment("a.jpg"), attachment("b.jpg"))

        assertEquals("Buttermilch", estimateRequestQuery("Buttermilch", images))
        assertEquals("Buttermilch", estimateDraftQuery("Buttermilch", images))
    }

    @Test
    fun oneUnlabelledPhotoAsksAboutASinglePhoto() {
        val images = listOf(attachment("plate.jpg"))

        assertEquals("Estimate the meal from the attached photo.", estimateRequestQuery("", images))
        assertEquals("Photo: plate.jpg", estimateDraftQuery("", images))
    }

    @Test
    fun severalPhotosAskAboutPhotosInThePlural() {
        val images = listOf(attachment("a.jpg"), attachment("b.jpg"), attachment("c.jpg"))

        assertEquals("Estimate the meal from the attached photos.", estimateRequestQuery("", images))
        assertEquals("Photos (3)", estimateDraftQuery("", images))
    }

    @Test
    fun nothingAtAllProducesNoQuery() {
        assertEquals("", estimateRequestQuery("", emptyList()))
        assertEquals("", estimateDraftQuery("", emptyList()))
    }

    @Test
    fun aWhitespaceOnlyQueryCountsAsBlank() {
        val images = listOf(attachment("plate.jpg"))

        assertEquals("Estimate the meal from the attached photo.", estimateRequestQuery("   ", images))
        assertEquals("Photo: plate.jpg", estimateDraftQuery("   ", images))
        assertEquals("", estimateRequestQuery("   ", emptyList()))
    }

    // --- Branch decision ---

    @Test
    fun noCachedMatchRunsAnEstimateForATypedQuery() {
        val request = runEstimate(planEstimate("Buttermilch 200g", emptyList(), cachedMatch = null))

        assertEquals("Buttermilch 200g", request.requestQuery)
        assertEquals("Buttermilch 200g", request.finalDraftQuery)
        assertTrue(request.imageAttachments.isEmpty())
    }

    @Test
    fun noCachedMatchRunsAPhotoOnlyEstimateAndKeepsTheAttachments() {
        val images = listOf(attachment("plate.jpg"), attachment("side.jpg"))

        val request = runEstimate(planEstimate("", images, cachedMatch = null))

        assertEquals("Estimate the meal from the attached photos.", request.requestQuery)
        assertEquals("Photos (2)", request.finalDraftQuery)
        assertEquals(images, request.imageAttachments)
    }

    @Test
    fun noCachedMatchAndNothingToEstimateReportsTheMissingInputMessage() {
        val plan = planEstimate("", emptyList(), cachedMatch = null)

        assertTrue("expected missing input, got $plan", plan is EstimatePlan.MissingInput)
        assertEquals(missingInputMessage, (plan as EstimatePlan.MissingInput).message)
    }

    @Test
    fun aWhitespaceOnlyQueryWithoutPhotosReportsTheMissingInputMessage() {
        val plan = planEstimate("   ", emptyList(), cachedMatch = null)

        assertTrue("expected missing input, got $plan", plan is EstimatePlan.MissingInput)
        assertEquals(missingInputMessage, (plan as EstimatePlan.MissingInput).message)
    }

    @Test
    fun aCachedMatchServesFromCacheAndKeepsTheEstimateAsAFallback() {
        val match = cachedMatch("Buttermilch 200g")

        val served = servedFromCache(planEstimate("Buttermilch 200g", emptyList(), match))

        assertSame(match, served.match)
        assertEquals("Buttermilch 200g", served.pendingRequest.requestQuery)
        assertEquals("Buttermilch 200g", served.pendingRequest.finalDraftQuery)
    }

    @Test
    fun aCachedMatchStillCarriesTheAttachmentsIntoTheFallbackEstimate() {
        val images = listOf(attachment("plate.jpg"))
        val match = cachedMatch("Buttermilch 200g")

        val served = servedFromCache(planEstimate("Buttermilch 200g", images, match))

        assertEquals(images, served.pendingRequest.imageAttachments)
    }

    @Test
    fun aBlankRequestQueryNeverMatchesTheCacheSoTheCacheBranchAlwaysHasAFallback() {
        // The invariant behind ServeFromCache.pendingRequest being non-null: the capture flow looks
        // the cache up by the request query this rule builds, and a blank key cannot match a row.
        val cache = listOf(cachedDraft("Buttermilch 200g").toCachedMealEntity()!!)

        val requestQuery = estimateRequestQuery("   ", emptyList())

        assertEquals("", requestQuery)
        assertNull(findCachedMealMatch(cache, requestQuery))
    }

    @Test
    fun theCacheLookupKeyIsTheQueryTheRequestIsBuiltWith() {
        val images = listOf(attachment("plate.jpg"))
        val cache = listOf(cachedDraft("Estimate the meal from the attached photo.").toCachedMealEntity()!!)

        val requestQuery = estimateRequestQuery("", images)
        val match = findCachedMealMatch(cache, requestQuery)
        val served = servedFromCache(planEstimate("", images, match))

        assertEquals(requestQuery, served.pendingRequest.requestQuery)
        assertEquals("Photo: plate.jpg", served.pendingRequest.finalDraftQuery)
    }
}
