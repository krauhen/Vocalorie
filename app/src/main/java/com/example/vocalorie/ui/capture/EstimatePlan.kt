package com.example.vocalorie.ui.capture

import com.example.vocalorie.data.CachedMealMatch
import com.example.vocalorie.ui.voice.GalleryImageAttachment

/**
 * A resolved nutrition-estimate request: what the model is asked, what the reviewed draft is
 * labelled with, and the photos that go with it.
 */
data class EstimateRequest(
    val requestQuery: String,
    val finalDraftQuery: String,
    val imageAttachments: List<GalleryImageAttachment>,
)

/**
 * What tapping Estimate should do, decided before any I/O runs.
 *
 * The two productive outcomes are serving a cached meal and running an estimate; [MissingInput]
 * exists because the same tap can also carry nothing to estimate at all.
 */
sealed interface EstimatePlan {
    /**
     * A cache row already answers this request. [pendingRequest] is what runs if the user rejects
     * the cached draft — it is always present, because a request with nothing to estimate cannot
     * match a cache row in the first place.
     */
    data class ServeFromCache(
        val match: CachedMealMatch,
        val pendingRequest: EstimateRequest,
    ) : EstimatePlan

    /** No cache row matched, so the request goes to the model. */
    data class RunEstimate(val request: EstimateRequest) : EstimatePlan

    /** Neither a query nor a photo was supplied; [message] is shown to the user. */
    data class MissingInput(val message: String) : EstimatePlan
}

/**
 * The query sent to the model: the user's text, or a stand-in instruction when only photos were
 * attached. This is also the key the meal cache is looked up by, so a caller performing the lookup
 * asks for it first and passes the result to [planEstimate].
 */
fun estimateRequestQuery(query: String, images: List<GalleryImageAttachment>): String = query.ifBlank {
    if (images.isNotEmpty()) "Estimate the meal from the attached photo${if (images.size > 1) "s" else ""}." else ""
}

/** The label the reviewed draft carries: the user's text, or a description of the photos. */
fun estimateDraftQuery(query: String, images: List<GalleryImageAttachment>): String = query.ifBlank {
    when (images.size) {
        0 -> ""
        1 -> "Photo: ${images.first().label}"
        else -> "Photos (${images.size})"
    }
}

/**
 * Builds the estimate request from the query and attachments, and decides whether [cachedMatch]
 * can serve it.
 *
 * Pure: no clock, no storage, no Android. [cachedMatch] is the caller's lookup result for
 * `estimateRequestQuery(query, images)`, kept as a parameter so the lookup can become a suspending
 * repository call without making this rule impure.
 */
fun planEstimate(
    query: String,
    images: List<GalleryImageAttachment>,
    cachedMatch: CachedMealMatch?,
): EstimatePlan {
    val request = EstimateRequest(
        requestQuery = estimateRequestQuery(query, images),
        finalDraftQuery = estimateDraftQuery(query, images),
        imageAttachments = images,
    )
    return when {
        cachedMatch != null -> EstimatePlan.ServeFromCache(match = cachedMatch, pendingRequest = request)
        request.requestQuery.isBlank() -> EstimatePlan.MissingInput(MISSING_ESTIMATE_INPUT_MESSAGE)
        else -> EstimatePlan.RunEstimate(request)
    }
}

private const val MISSING_ESTIMATE_INPUT_MESSAGE = "Enter a nutrition query or attach a photo."
