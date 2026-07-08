package com.example.vocalorie.settings

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.vocalorie.BuildConfig
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ToolSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): ToolSettings = ToolSettings(
        braveApiKey = getBraveApiKey() ?: defaultBraveApiKey(),
        maxResearchToolCalls = getMaxResearchToolCalls(),
        maxAgentIterations = getMaxAgentIterations(),
        openAiModelChoiceName = getOpenAiModelChoiceName(),
    )

    fun savedBraveKeyLabel(): String? = ToolSettingsLabels.braveKeyLabel(
        prefs.getString(KEY_BRAVE_LAST4, null) ?: defaultBraveApiKey()?.let(ToolSettingsLabels::last4),
    )

    @Synchronized
    fun saveBraveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "Brave API key cannot be blank." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))

        prefs.edit()
            .putString(KEY_BRAVE_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_BRAVE_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_BRAVE_LAST4, ToolSettingsLabels.last4(trimmed))
            .apply()
    }

    @Synchronized
    fun clearBraveApiKey() {
        prefs.edit().remove(KEY_BRAVE_IV).remove(KEY_BRAVE_CIPHERTEXT).remove(KEY_BRAVE_LAST4).apply()
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

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8)
        }.getOrElse {
            clearBraveApiKey()
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
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
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "vocalorie_tool_settings"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val KEY_BRAVE_IV = "brave_iv"
        private const val KEY_BRAVE_CIPHERTEXT = "brave_ciphertext"
        private const val KEY_BRAVE_LAST4 = "brave_last4"
        private const val KEY_MAX_RESEARCH_TOOL_CALLS = "max_research_tool_calls"
        private const val KEY_MAX_AGENT_ITERATIONS = "max_agent_iterations"
        private const val KEY_OPENAI_MODEL_CHOICE = "openai_model_choice"
    }
}
