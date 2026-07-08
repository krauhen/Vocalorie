package com.example.vocalorie.settings

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel

data class ToolSettings(
    val braveApiKey: String? = null,
    val maxResearchToolCalls: Int = DEFAULT_MAX_RESEARCH_TOOL_CALLS,
    val maxAgentIterations: Int = DEFAULT_MAX_AGENT_ITERATIONS,
    val openAiModelChoiceName: String = OpenAiModelChoice.default.name,
) {
    val hasBraveApiKey: Boolean get() = !braveApiKey.isNullOrBlank()
    val openAiModelChoice: OpenAiModelChoice get() = OpenAiModelChoice.fromName(openAiModelChoiceName)

    companion object {
        const val MIN_MAX_RESEARCH_TOOL_CALLS = 0
        const val MAX_MAX_RESEARCH_TOOL_CALLS = 32
        const val DEFAULT_MAX_RESEARCH_TOOL_CALLS = 8

        const val MIN_MAX_AGENT_ITERATIONS = 16
        const val MAX_MAX_AGENT_ITERATIONS = 128
        const val DEFAULT_MAX_AGENT_ITERATIONS = 64

        val maxResearchToolCallsRange: IntRange = MIN_MAX_RESEARCH_TOOL_CALLS..MAX_MAX_RESEARCH_TOOL_CALLS
        val maxAgentIterationsRange: IntRange = MIN_MAX_AGENT_ITERATIONS..MAX_MAX_AGENT_ITERATIONS
    }
}

enum class OpenAiModelChoice(val label: String) {
    GPT4O("GPT-4o"),
    GPT4OMINI("GPT-4o mini"),
    GPT41MINI("GPT-4.1 mini"),
    GPT54MINI("GPT-5.4 mini"),
    ;

    companion object {
        val default: OpenAiModelChoice = GPT54MINI

        fun fromName(name: String?): OpenAiModelChoice = values().firstOrNull { it.name == name } ?: default
    }

    val model: LLModel
        get() = when (this) {
            GPT4O -> OpenAIModels.Chat.GPT4o
            GPT4OMINI -> OpenAIModels.Chat.GPT4oMini
            GPT41MINI -> OpenAIModels.Chat.GPT4_1Mini
            GPT54MINI -> OpenAIModels.Chat.GPT5_4Mini
        }
}

object ToolSettingsLabels {
    fun last4(apiKey: String): String = apiKey.trim().takeLast(4)

    fun braveKeyLabel(last4: String?): String? {
        val suffix = last4?.trim().orEmpty()
        return if (suffix.isBlank()) null else "Saved Brave key ending in $suffix"
    }

    fun braveModeLabel(hasKey: Boolean): String =
        if (hasKey) "Brave Search: real API configured" else "Brave Search: API key required"

    fun webFetchModeLabel(): String = "WebFetch: real HTTP fetch enabled"

    fun openAiModelLabel(choice: OpenAiModelChoice): String = choice.label

    fun openAiModelLabel(choiceName: String?): String = OpenAiModelChoice.fromName(choiceName).label
}
