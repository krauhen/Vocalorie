package com.example.vocalorie.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSettingsStoreTest {
    @Test
    fun numericSettingsPersistThroughTheStore() {
        val prefs = InMemorySharedPreferences()
        val store = ToolSettingsStore(testContext(prefs))

        store.saveMaxResearchToolCalls(13)
        store.saveMaxAgentIterations(72)

        val loaded = store.get()

        assertEquals(13, loaded.maxResearchToolCalls)
        assertEquals(72, loaded.maxAgentIterations)
    }

    @Test
    fun getFallsBackToExplicitDefaultsWhenNothingIsSaved() {
        val store = ToolSettingsStore(testContext(InMemorySharedPreferences()))

        val loaded = store.get()

        assertEquals(ToolSettings.DEFAULT_MAX_RESEARCH_TOOL_CALLS, loaded.maxResearchToolCalls)
        assertEquals(ToolSettings.DEFAULT_MAX_AGENT_ITERATIONS, loaded.maxAgentIterations)
        assertEquals(OpenAiModelChoice.GPT54MINI, loaded.openAiModelChoice)
    }

    @Test
    fun savesRejectValuesOutsideTheModelRange() {
        val store = ToolSettingsStore(testContext(InMemorySharedPreferences()))

        assertThrows(IllegalArgumentException::class.java) { store.saveMaxResearchToolCalls(-1) }
        assertThrows(IllegalArgumentException::class.java) { store.saveMaxResearchToolCalls(33) }
        assertThrows(IllegalArgumentException::class.java) { store.saveMaxAgentIterations(15) }
        assertThrows(IllegalArgumentException::class.java) { store.saveMaxAgentIterations(129) }
    }

    @Test
    fun systemPromptOverridePersistsAndClearsThroughTheStore() {
        val store = ToolSettingsStore(testContext(InMemorySharedPreferences()))

        assertEquals(null, store.get().systemPromptOverride)

        store.saveSystemPromptOverride("Custom nutrition prompt.")
        assertEquals("Custom nutrition prompt.", store.get().systemPromptOverride)

        store.clearSystemPromptOverride()
        assertEquals(null, store.get().systemPromptOverride)
    }

    @Test
    fun systemPromptOverrideRejectsBlankValues() {
        val store = ToolSettingsStore(testContext(InMemorySharedPreferences()))

        assertThrows(IllegalArgumentException::class.java) { store.saveSystemPromptOverride("   ") }
    }

    @Test
    fun unreadableBraveKeyKeepsCiphertextAndReportsReEnter() {
        val prefs = InMemorySharedPreferences()
        // There is no AndroidKeyStore on the JVM, so reading this stored secret always fails,
        // which is exactly the post-restore condition that used to wipe the key.
        prefs.edit()
            .putString("brave_iv", "aXYtYnl0ZXM=")
            .putString("brave_ciphertext", "Y2lwaGVydGV4dA==")
            .putString("brave_last4", "efgh")
            .apply()
        val store = ToolSettingsStore(testContext(prefs))

        store.get()

        assertTrue(prefs.contains("brave_ciphertext"))
        assertTrue(prefs.contains("brave_iv"))
        assertEquals(SecretKeyState.UNREADABLE, store.braveKeyState())
        assertEquals("Saved Brave key could not be read. Re-enter it.", store.savedBraveKeyLabel())
    }

    @Test
    fun clearingTheBraveKeyRemovesEveryStoredField() {
        val prefs = InMemorySharedPreferences()
        prefs.edit()
            .putString("brave_iv", "aXYtYnl0ZXM=")
            .putString("brave_ciphertext", "Y2lwaGVydGV4dA==")
            .putString("brave_last4", "efgh")
            .putBoolean("brave_read_failed", true)
            .apply()
        val store = ToolSettingsStore(testContext(prefs))

        store.clearBraveApiKey()

        assertEquals(false, prefs.contains("brave_iv"))
        assertEquals(false, prefs.contains("brave_ciphertext"))
        assertEquals(false, prefs.contains("brave_last4"))
        assertEquals(false, prefs.contains("brave_read_failed"))
    }
}
