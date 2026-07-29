package com.example.vocalorie.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretKeyLabelsTest {
    @Test
    fun everyStoredKeyRevealsTheSameAmountOfItself() {
        assertEquals("wxyz", SecretKeyLabels.last4("  test-key-abcd-wxyz  "))
        assertEquals(
            SecretKeyLabels.last4("sk-live-abcd-wxyz").length,
            SecretKeyLabels.last4("brave-abcd-efgh").length,
        )
    }

    @Test
    fun bothStoreLabelsComeFromTheSharedMasking() {
        assertEquals(OpenAiApiKeyLabels.last4("  key-1234  "), SecretKeyLabels.last4("  key-1234  "))
        assertEquals(ToolSettingsLabels.last4("  key-1234  "), SecretKeyLabels.last4("  key-1234  "))
        assertEquals("Saved key ending in 1234", SecretKeyLabels.savedKeyLabel("1234"))
        assertEquals("Saved Brave key ending in 1234", SecretKeyLabels.savedKeyLabel("1234", "Brave key"))
    }

    @Test
    fun savedKeyLabelIsNullWithoutSuffix() {
        assertNull(SecretKeyLabels.savedKeyLabel("   "))
        assertNull(SecretKeyLabels.savedKeyLabel(null))
        assertNull(SecretKeyLabels.savedKeyLabel(null, "Brave key"))
    }

    @Test
    fun unreadableLabelAsksForAReEntryInsteadOfClaimingNoKey() {
        assertEquals("Saved key could not be read. Re-enter it.", SecretKeyLabels.unreadableKeyLabel())
        assertEquals(
            "Saved Brave key could not be read. Re-enter it.",
            SecretKeyLabels.unreadableKeyLabel("Brave key"),
        )
        assertEquals(OpenAiApiKeyLabels.unreadableLabel(), SecretKeyLabels.unreadableKeyLabel())
        assertEquals(ToolSettingsLabels.unreadableBraveKeyLabel(), SecretKeyLabels.unreadableKeyLabel("Brave key"))
    }

    @Test
    fun stateSeparatesUnreadableFromAbsent() {
        assertEquals(
            SecretKeyState.NOT_CONFIGURED,
            SecretKeyLabels.stateOf(hasStoredSecret = false, readFailed = false),
        )
        assertEquals(
            SecretKeyState.SAVED,
            SecretKeyLabels.stateOf(hasStoredSecret = true, readFailed = false),
        )
        assertEquals(
            SecretKeyState.UNREADABLE,
            SecretKeyLabels.stateOf(hasStoredSecret = true, readFailed = true),
        )
    }

    @Test
    fun aBuildTimeFallbackKeyCountsAsSavedButCannotBeUnreadable() {
        assertEquals(
            SecretKeyState.SAVED,
            SecretKeyLabels.stateOf(hasStoredSecret = false, readFailed = false, hasFallbackSecret = true),
        )
        // A stale read-failure flag with nothing stored must not hide the fallback key.
        assertEquals(
            SecretKeyState.SAVED,
            SecretKeyLabels.stateOf(hasStoredSecret = false, readFailed = true, hasFallbackSecret = true),
        )
    }
}
