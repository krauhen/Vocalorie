package com.example.vocalorie.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.vocalorie.BuildConfig

class ToolSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val codec = KeystoreSecretCodec(KEY_ALIAS)

    fun get(): ToolSettings = ToolSettings(
        braveApiKey = getBraveApiKey() ?: defaultBraveApiKey(),
        maxResearchToolCalls = getMaxResearchToolCalls(),
        maxAgentIterations = getMaxAgentIterations(),
        openAiModelChoiceName = getOpenAiModelChoiceName(),
        systemPromptOverride = savedSystemPromptOverride(),
    )

    fun savedSystemPromptOverride(): String? = prefs.getString(KEY_SYSTEM_PROMPT_OVERRIDE, null)

    @Synchronized
    fun saveSystemPromptOverride(prompt: String) {
        val trimmed = prompt.trim()
        require(trimmed.isNotEmpty()) { "System prompt cannot be blank." }

        prefs.edit().putString(KEY_SYSTEM_PROMPT_OVERRIDE, trimmed).apply()
    }

    @Synchronized
    fun clearSystemPromptOverride() {
        prefs.edit().remove(KEY_SYSTEM_PROMPT_OVERRIDE).apply()
    }

    fun savedBraveKeyLabel(): String? = when (braveKeyState()) {
        SecretKeyState.UNREADABLE -> ToolSettingsLabels.unreadableBraveKeyLabel()
        else -> ToolSettingsLabels.braveKeyLabel(
            prefs.getString(KEY_BRAVE_LAST4, null) ?: defaultBraveApiKey()?.let(ToolSettingsLabels::last4),
        )
    }

    fun braveKeyState(): SecretKeyState = SecretKeyLabels.stateOf(
        hasStoredSecret = prefs.contains(KEY_BRAVE_CIPHERTEXT),
        readFailed = prefs.getBoolean(KEY_BRAVE_READ_FAILED, false),
        hasFallbackSecret = defaultBraveApiKey() != null,
    )

    @Synchronized
    fun saveBraveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "Brave API key cannot be blank." }

        val encrypted = codec.encrypt(trimmed)

        prefs.edit()
            .putString(KEY_BRAVE_IV, encrypted.ivBase64)
            .putString(KEY_BRAVE_CIPHERTEXT, encrypted.ciphertextBase64)
            .putString(KEY_BRAVE_LAST4, ToolSettingsLabels.last4(trimmed))
            .remove(KEY_BRAVE_READ_FAILED)
            .apply()
    }

    @Synchronized
    fun clearBraveApiKey() {
        prefs.edit()
            .remove(KEY_BRAVE_IV)
            .remove(KEY_BRAVE_CIPHERTEXT)
            .remove(KEY_BRAVE_LAST4)
            .remove(KEY_BRAVE_READ_FAILED)
            .apply()
    }

    @Synchronized
    fun saveMaxResearchToolCalls(maxResearchToolCalls: Int) {
        require(maxResearchToolCalls in ToolSettings.maxResearchToolCallsRange) {
            "Max research tool calls must be ${ToolSettings.MIN_MAX_RESEARCH_TOOL_CALLS}-${ToolSettings.MAX_MAX_RESEARCH_TOOL_CALLS}."
        }

        prefs.edit().putInt(KEY_MAX_RESEARCH_TOOL_CALLS, maxResearchToolCalls).apply()
    }

    @Synchronized
    fun saveMaxAgentIterations(maxAgentIterations: Int) {
        require(maxAgentIterations in ToolSettings.maxAgentIterationsRange) {
            "Max agent iterations must be ${ToolSettings.MIN_MAX_AGENT_ITERATIONS}-${ToolSettings.MAX_MAX_AGENT_ITERATIONS}."
        }

        prefs.edit().putInt(KEY_MAX_AGENT_ITERATIONS, maxAgentIterations).apply()
    }

    @Synchronized
    fun saveOpenAiModelChoice(choiceName: String) {
        require(choiceName == OpenAiModelChoice.fromName(choiceName).name) {
            "Unsupported OpenAI model choice."
        }

        prefs.edit().putString(KEY_OPENAI_MODEL_CHOICE, choiceName).apply()
    }

    @Synchronized
    private fun getBraveApiKey(): String? {
        val iv = prefs.getString(KEY_BRAVE_IV, null) ?: return null
        val ciphertext = prefs.getString(KEY_BRAVE_CIPHERTEXT, null) ?: return null

        // A failed read must never destroy the secret: keep the ciphertext and remember that it
        // could not be read, so the label says "re-enter" instead of "no key configured".
        val decrypted = codec.decrypt(iv, ciphertext)
        rememberBraveReadFailed(decrypted == null)
        return decrypted
    }

    private fun rememberBraveReadFailed(failed: Boolean) {
        if (prefs.getBoolean(KEY_BRAVE_READ_FAILED, false) == failed) return

        if (failed) {
            prefs.edit().putBoolean(KEY_BRAVE_READ_FAILED, true).apply()
        } else {
            prefs.edit().remove(KEY_BRAVE_READ_FAILED).apply()
        }
    }

    private fun defaultBraveApiKey(): String? = BuildConfig.DEFAULT_BRAVE_API_KEY.trim().takeIf { it.isNotEmpty() }

    private fun getMaxResearchToolCalls(): Int = prefs
        .getInt(KEY_MAX_RESEARCH_TOOL_CALLS, ToolSettings.DEFAULT_MAX_RESEARCH_TOOL_CALLS)
        .coerceIn(ToolSettings.maxResearchToolCallsRange)

    private fun getMaxAgentIterations(): Int = prefs
        .getInt(KEY_MAX_AGENT_ITERATIONS, ToolSettings.DEFAULT_MAX_AGENT_ITERATIONS)
        .coerceIn(ToolSettings.maxAgentIterationsRange)

    private fun getOpenAiModelChoiceName(): String = OpenAiModelChoice.fromName(
        prefs.getString(KEY_OPENAI_MODEL_CHOICE, null),
    ).name

    companion object {
        const val PREFS_NAME = "tool_settings_store"
        private const val KEY_ALIAS = "vocalorie_tool_settings"
        private const val KEY_BRAVE_IV = "brave_iv"
        private const val KEY_BRAVE_CIPHERTEXT = "brave_ciphertext"
        private const val KEY_BRAVE_LAST4 = "brave_last4"
        private const val KEY_BRAVE_READ_FAILED = "brave_read_failed"
        private const val KEY_MAX_RESEARCH_TOOL_CALLS = "max_research_tool_calls"
        private const val KEY_MAX_AGENT_ITERATIONS = "max_agent_iterations"
        private const val KEY_OPENAI_MODEL_CHOICE = "openai_model_choice"
        private const val KEY_SYSTEM_PROMPT_OVERRIDE = "system_prompt_override"
    }
}
