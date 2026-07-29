package com.example.vocalorie.settings

/** What the app knows about a stored secret, so an unreadable key never looks like a missing one. */
enum class SecretKeyState {
    /** Nothing is stored: the user has to enter a key. */
    NOT_CONFIGURED,

    /** A secret is stored and the last read of it succeeded (or was never attempted). */
    SAVED,

    /** A secret is stored but could not be decrypted; the ciphertext is kept, not deleted. */
    UNREADABLE,
}

/**
 * The single masking implementation for stored secrets, so every key reveals exactly the same
 * amount of itself.
 */
object SecretKeyLabels {
    /** How much of a stored secret is ever shown back to the user. */
    private const val VISIBLE_SUFFIX_LENGTH = 4

    fun last4(apiKey: String): String = apiKey.trim().takeLast(VISIBLE_SUFFIX_LENGTH)

    /** `null` when there is no suffix to show, which the UI reads as "no key configured". */
    fun savedKeyLabel(last4: String?, keyName: String = DEFAULT_KEY_NAME): String? {
        val suffix = last4?.trim().orEmpty()
        return if (suffix.isBlank()) null else "Saved $keyName ending in $suffix"
    }

    fun unreadableKeyLabel(keyName: String = DEFAULT_KEY_NAME): String =
        "Saved $keyName could not be read. Re-enter it."

    /**
     * Resolves the stored-secret state without ever implying that an unreadable secret is absent.
     *
     * @param hasStoredSecret whether ciphertext is present in preferences
     * @param readFailed whether the last decrypt attempt failed
     * @param hasFallbackSecret whether a build-time default key stands in for a stored one
     */
    fun stateOf(hasStoredSecret: Boolean, readFailed: Boolean, hasFallbackSecret: Boolean = false): SecretKeyState = when {
        hasStoredSecret && readFailed -> SecretKeyState.UNREADABLE
        hasStoredSecret || hasFallbackSecret -> SecretKeyState.SAVED
        else -> SecretKeyState.NOT_CONFIGURED
    }

    private const val DEFAULT_KEY_NAME = "key"
}
