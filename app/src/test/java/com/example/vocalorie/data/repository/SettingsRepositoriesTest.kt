package com.example.vocalorie.data.repository

import androidx.compose.ui.graphics.Color
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.settings.InMemorySharedPreferences
import com.example.vocalorie.settings.OpenAiApiKeyStore
import com.example.vocalorie.settings.SecretKeyState
import com.example.vocalorie.settings.ThemeSettingsStore
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.settings.ToolSettingsStore
import com.example.vocalorie.settings.testContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two settings repositories. There is no AndroidKeyStore on the JVM, so a decrypt here fails
 * exactly as it would on a device whose KeyStore entry vanished — which makes the unreadable-key
 * path testable, and pins that the failure is reported rather than reported as "no key".
 */
class SettingsRepositoriesTest {

    // --- ThemeSettingsRepository ---

    @Test
    fun themeSnapshotReadsEverySettingsSliceInOnePass() = runTest {
        val store = ThemeSettingsStore(testContext(InMemorySharedPreferences()))
        val repository = ThemeSettingsRepository(store)
        repository.saveBaseCaloriesBurned(2100)
        repository.saveKcalPerStep(30 / 1000.0)
        repository.saveNutritionGoals(NutritionGoals(calorieGoalKcal = 1900, proteinPercent = 30, carbsPercent = 40, fatPercent = 30))

        val snapshot = repository.snapshot()

        assertEquals(2100, snapshot.baseCaloriesBurned)
        assertEquals(0.03, snapshot.kcalPerStep, 0.0)
        assertEquals(1900, snapshot.nutritionGoals.calorieGoalKcal)
        assertEquals(store.get(), snapshot.mealColors)
        assertEquals(store.getActivityColors(), snapshot.activityColors)
    }

    @Test
    fun themeColorWritesRoundTripThroughTheSnapshot() = runTest {
        val repository = ThemeSettingsRepository(ThemeSettingsStore(testContext(InMemorySharedPreferences())))
        val primary = Color(0xFF123456)
        val activityOutline = Color(0xFF654321)

        repository.savePrimary(primary)
        repository.saveActivityOutline(activityOutline)

        val snapshot = repository.snapshot()
        assertEquals(primary, snapshot.mealColors.primary)
        assertEquals(activityOutline, snapshot.activityColors.outline)
    }

    @Test
    fun themeReadsAndWritesRunOnTheInjectedDispatcher() = runTest {
        val dispatcher = CountingDispatcher()
        val repository = ThemeSettingsRepository(ThemeSettingsStore(testContext(InMemorySharedPreferences())), dispatcher)

        repository.saveBaseCaloriesBurned(2100)
        val afterWrite = dispatcher.dispatches
        repository.snapshot()

        assertTrue("the write must dispatch", afterWrite > 0)
        assertTrue("the snapshot read must dispatch too", dispatcher.dispatches > afterWrite)
    }

    // --- SecretRepository ---

    @Test
    fun anUnreadableOpenAiKeyIsReportedAsUnreadableNotAsMissing() = runTest {
        val prefs = storedKeyPrefs()
        val repository = secretRepository(apiKeyPrefs = prefs)

        // Before any read is attempted, a stored key simply reads as saved.
        assertEquals(SecretKeyState.SAVED, repository.openAiKeyStatus().state)
        assertEquals("Saved key ending in 1234", repository.openAiKeyStatus().label)

        assertNull("the key cannot be decrypted on the JVM", repository.openAiApiKey())

        val afterFailedRead = repository.openAiKeyStatus()
        assertEquals(SecretKeyState.UNREADABLE, afterFailedRead.state)
        assertEquals("Saved key could not be read. Re-enter it.", afterFailedRead.label)
        assertTrue("the ciphertext must be kept, not deleted", prefs.contains("ciphertext"))
    }

    @Test
    fun aMissingOpenAiKeyHasNoLabel() = runTest {
        val repository = secretRepository()

        val status = repository.openAiKeyStatus()

        assertEquals(SecretKeyState.NOT_CONFIGURED, status.state)
        assertNull(status.label)
        assertNull(repository.openAiApiKey())
    }

    @Test
    fun toolSettingsAreReadWithTheirBraveLabelInOnePass() = runTest {
        val prefs = InMemorySharedPreferences()
        val repository = secretRepository(toolPrefs = prefs)
        repository.saveMaxResearchToolCalls(3)
        repository.saveMaxAgentIterations(32)

        val status = repository.toolSettingsStatus()

        assertEquals(3, status.settings.maxResearchToolCalls)
        assertEquals(32, status.settings.maxAgentIterations)
        assertEquals(ToolSettings().openAiModelChoiceName, status.settings.openAiModelChoiceName)
    }

    @Test
    fun theSystemPromptOverrideRoundTripsAndClears() = runTest {
        val repository = secretRepository()

        repository.saveSystemPromptOverride("  Be terse.  ")
        assertEquals("Be terse.", repository.toolSettingsStatus().settings.systemPromptOverride)

        repository.clearSystemPromptOverride()
        assertNull(repository.toolSettingsStatus().settings.systemPromptOverride)
    }

    @Test
    fun keyCryptoRunsOnTheInjectedDispatcherRatherThanTheCallersThread() = runTest {
        val dispatcher = CountingDispatcher()
        val repository = secretRepository(apiKeyPrefs = storedKeyPrefs(), dispatcher = dispatcher)

        // This is the decrypt call: it must not run on the caller's thread.
        repository.openAiApiKey()

        assertTrue("the decrypt must dispatch", dispatcher.dispatches > 0)
    }

    private fun secretRepository(
        apiKeyPrefs: InMemorySharedPreferences = InMemorySharedPreferences(),
        toolPrefs: InMemorySharedPreferences = InMemorySharedPreferences(),
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = SecretRepository(
        apiKeyStore = OpenAiApiKeyStore(testContext(apiKeyPrefs)),
        toolSettingsStore = ToolSettingsStore(testContext(toolPrefs)),
        dispatcher = dispatcher,
    )

    /** Preferences holding a key that cannot be decrypted, the same shape `OpenAiApiKeyStoreTest` uses. */
    private fun storedKeyPrefs(): InMemorySharedPreferences = InMemorySharedPreferences().apply {
        edit()
            .putString("iv", "aXYtYnl0ZXM=")
            .putString("ciphertext", "Y2lwaGVydGV4dA==")
            .putString("last4", "1234")
            .apply()
    }
}
