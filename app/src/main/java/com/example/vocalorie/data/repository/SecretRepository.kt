package com.example.vocalorie.data.repository

import com.example.vocalorie.settings.OpenAiApiKeyStore
import com.example.vocalorie.settings.SecretKeyState
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.settings.ToolSettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The stored OpenAI key's state plus the label the UI shows for it, read in one pass. */
data class OpenAiKeyStatus(val state: SecretKeyState, val label: String?)

/** The tool settings plus the Brave key's label, read in one pass. */
data class ToolSettingsStatus(val settings: ToolSettings, val braveKeyLabel: String?)

/**
 * The BYOK secrets and the tool settings that carry one.
 *
 * This is the layer that keeps keystore crypto off the main thread: every read that decrypts, and
 * every write that encrypts, happens on [dispatcher]. Callers never wrap a call themselves.
 *
 * Takes the stores, not a `Context`.
 */
class SecretRepository(
    private val apiKeyStore: OpenAiApiKeyStore,
    private val toolSettingsStore: ToolSettingsStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Decrypts the stored OpenAI key, or `null` when none is stored or it cannot be read. */
    suspend fun openAiApiKey(): String? = withContext(dispatcher) { apiKeyStore.get() }

    suspend fun openAiKeyStatus(): OpenAiKeyStatus = withContext(dispatcher) {
        OpenAiKeyStatus(state = apiKeyStore.keyState(), label = apiKeyStore.displayLabel())
    }

    suspend fun saveOpenAiApiKey(apiKey: String) = withContext(dispatcher) { apiKeyStore.save(apiKey) }

    suspend fun clearOpenAiApiKey() = withContext(dispatcher) { apiKeyStore.clear() }

    /** The tool settings, including the decrypted Brave key when one is stored. */
    suspend fun toolSettingsStatus(): ToolSettingsStatus = withContext(dispatcher) {
        ToolSettingsStatus(
            settings = toolSettingsStore.get(),
            braveKeyLabel = toolSettingsStore.savedBraveKeyLabel(),
        )
    }

    suspend fun saveBraveApiKey(apiKey: String) = withContext(dispatcher) { toolSettingsStore.saveBraveApiKey(apiKey) }

    suspend fun clearBraveApiKey() = withContext(dispatcher) { toolSettingsStore.clearBraveApiKey() }

    suspend fun saveMaxResearchToolCalls(value: Int) =
        withContext(dispatcher) { toolSettingsStore.saveMaxResearchToolCalls(value) }

    suspend fun saveMaxAgentIterations(value: Int) =
        withContext(dispatcher) { toolSettingsStore.saveMaxAgentIterations(value) }

    suspend fun saveOpenAiModelChoice(choiceName: String) =
        withContext(dispatcher) { toolSettingsStore.saveOpenAiModelChoice(choiceName) }

    suspend fun saveSystemPromptOverride(prompt: String) =
        withContext(dispatcher) { toolSettingsStore.saveSystemPromptOverride(prompt) }

    suspend fun clearSystemPromptOverride() = withContext(dispatcher) { toolSettingsStore.clearSystemPromptOverride() }
}
