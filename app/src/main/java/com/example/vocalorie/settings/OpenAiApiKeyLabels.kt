package com.example.vocalorie.settings

/** OpenAI-key wording over the shared masking in [SecretKeyLabels]. */
object OpenAiApiKeyLabels {
    fun last4(apiKey: String): String = SecretKeyLabels.last4(apiKey)

    fun maskedLabel(last4: String?): String? = SecretKeyLabels.savedKeyLabel(last4)

    fun unreadableLabel(): String = SecretKeyLabels.unreadableKeyLabel()
}
