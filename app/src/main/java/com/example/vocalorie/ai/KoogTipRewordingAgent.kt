package com.example.vocalorie.ai

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.MessagePart
import com.example.vocalorie.settings.ToolSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Rewords the already-computed day-score tips. It never invents one.
 *
 * A deliberately separate entry point from [NutritionEstimator]: the nutrition-estimate prompt and
 * its DTOs are pinned word-for-word by `NutritionPromptContractTest`, and a stylistic side errand
 * has no business inside that contract. The caller validates the reply
 * (`validateRewordedTips`) and falls back to the rule wording on anything unexpected.
 */
interface TipRewordingAgent {

    /** One reworded line per entry in [tips], same order. Throws on any transport or model failure. */
    suspend fun reword(
        openAiApiKey: String,
        toolSettings: ToolSettings,
        tips: List<String>,
        dayContext: String,
    ): List<String>
}

class KoogTipRewordingAgent : TipRewordingAgent {

    override suspend fun reword(
        openAiApiKey: String,
        toolSettings: ToolSettings,
        tips: List<String>,
        dayContext: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val key = openAiApiKey.trim()
        require(key.isNotEmpty()) { "Enter an OpenAI API key." }
        require(tips.isNotEmpty()) { "There is nothing to reword." }

        val executor = KoogNutritionAgent.promptExecutors.get(key)
        val request = prompt("vocalorie-tip-rewording") {
            system(REWORDING_SYSTEM_PROMPT)
            user(
                buildString {
                    append("Today's numbers: ")
                    append(dayContext)
                    append("\n\nReword exactly these ")
                    append(tips.size)
                    append(" tips, one per line, in the same order:\n")
                    tips.forEach { append(it).append('\n') }
                },
            )
        }
        val response = withTimeoutOrNull(REWORDING_TIMEOUT_MS) {
            executor.execute(request, toolSettings.openAiModelChoice.model, emptyList())
        } ?: throw IllegalStateException("The tip rewording request timed out.")

        response.parts.filterIsInstance<MessagePart.Text>()
            .joinToString("\n") { it.text }
            .lines()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotEmpty() }
    }

    private companion object {
        const val REWORDING_TIMEOUT_MS: Long = 30_000L

        val REWORDING_SYSTEM_PROMPT = """
            You reword nutrition tips. You never add, remove, reorder or reinterpret one.
            Return exactly the same number of tips, in the same order, each keeping the same meaning.
            Each reworded tip must be between 5 and 10 words, blunt, second person, no emoji.
            Output only the tips, one per line, with no numbering, bullets or commentary.
        """.trimIndent()
    }
}
