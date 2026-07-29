package com.example.vocalorie.settings

import android.content.Context
import android.content.SharedPreferences

class OpenAiApiKeyStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val codec = KeystoreSecretCodec(KEY_ALIAS)

    @Synchronized
    fun save(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "API key cannot be blank." }

        val encrypted = codec.encrypt(trimmed)

        prefs.edit()
            .putString(KEY_IV, encrypted.ivBase64)
            .putString(KEY_CIPHERTEXT, encrypted.ciphertextBase64)
            .putString(KEY_LAST4, OpenAiApiKeyLabels.last4(trimmed))
            .remove(KEY_READ_FAILED)
            .apply()
    }

    @Synchronized
    fun get(): String? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null

        // A failed read must never destroy the secret: keep the ciphertext and remember that it
        // could not be read, so the label says "re-enter" instead of "no key configured".
        val decrypted = codec.decrypt(iv, ciphertext)
        rememberReadFailed(decrypted == null)
        return decrypted
    }

    fun hasSavedKey(): Boolean = prefs.contains(KEY_CIPHERTEXT)

    fun keyState(): SecretKeyState = SecretKeyLabels.stateOf(
        hasStoredSecret = hasSavedKey(),
        readFailed = prefs.getBoolean(KEY_READ_FAILED, false),
    )

    fun displayLabel(): String? = when (keyState()) {
        SecretKeyState.NOT_CONFIGURED -> null
        SecretKeyState.UNREADABLE -> OpenAiApiKeyLabels.unreadableLabel()
        SecretKeyState.SAVED -> OpenAiApiKeyLabels.maskedLabel(prefs.getString(KEY_LAST4, null))
    }

    @Synchronized
    fun clear() {
        prefs.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_LAST4)
            .remove(KEY_READ_FAILED)
            .apply()
    }

    private fun rememberReadFailed(failed: Boolean) {
        if (prefs.getBoolean(KEY_READ_FAILED, false) == failed) return

        if (failed) {
            prefs.edit().putBoolean(KEY_READ_FAILED, true).apply()
        } else {
            prefs.edit().remove(KEY_READ_FAILED).apply()
        }
    }

    companion object {
        const val PREFS_NAME = "openai_api_key_store"
        private const val KEY_ALIAS = "vocalorie_openai_api_key"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val KEY_LAST4 = "last4"
        private const val KEY_READ_FAILED = "read_failed"
    }
}
