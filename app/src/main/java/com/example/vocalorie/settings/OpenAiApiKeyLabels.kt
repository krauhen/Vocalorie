package com.example.vocalorie.settings

object OpenAiApiKeyLabels {
    fun last4(apiKey: String): String = apiKey.trim().takeLast(4)

    fun maskedLabel(last4: String?): String? {
        val suffix = last4?.trim().orEmpty()
        return if (suffix.isBlank()) null else "Saved key ending in $suffix"
    }
}
