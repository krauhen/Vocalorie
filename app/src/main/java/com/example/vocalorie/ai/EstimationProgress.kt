package com.example.vocalorie.ai

/**
 * The closed set of semantic steps an estimate can be in, as narrated to [NutritionEstimator.estimate]'s
 * `onProgress` callback.
 *
 * Deliberately pure Kotlin with no display strings (design D2 in the `narrate-estimation-progress`
 * change): the agent layer owns only which step it is in, and the UI owns the wording.
 */
sealed class EstimationProgress {
    /** Before the grounding decision is made. */
    data object Preparing : EstimationProgress()

    /** The research agent is looking for candidate sources via web search. */
    data object SearchingSources : EstimationProgress()

    /** The research agent is fetching one specific page. [url] is a full normalized URL. */
    data class ReadingSource(val url: String) : EstimationProgress()

    /** The estimating call, including any retries, is in flight. */
    data object CalculatingNutrition : EstimationProgress()
}
