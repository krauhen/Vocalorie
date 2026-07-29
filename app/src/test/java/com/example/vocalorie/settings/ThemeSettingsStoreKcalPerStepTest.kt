package com.example.vocalorie.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the step-burn setting: what the user typed is what Settings reads back. */
class ThemeSettingsStoreKcalPerStepTest {
    @Test
    fun thirtyPer1000StepsRoundTripsAsThirty() {
        val store = ThemeSettingsStore(testContext(InMemorySharedPreferences()))

        // Settings divides the typed "30" by 1,000 before saving.
        store.saveKcalPerStep(30 / 1000.0)

        assertEquals(0.03, store.getKcalPerStep(), 0.0)
        // Settings shows the value multiplied back up.
        assertEquals("30.0", (store.getKcalPerStep() * 1000).toString())
    }

    @Test
    fun theObservedFloatDefectValueCanNoLongerBeProduced() {
        val store = ThemeSettingsStore(testContext(InMemorySharedPreferences()))

        store.saveKcalPerStep(30 / 1000.0)

        val perThousand = store.getKcalPerStep() * 1000
        assertTrue("29.999999329447746 must not come back", perThousand.toString() != "29.999999329447746")
        assertEquals(30.0, perThousand, 0.0)
    }

    @Test
    fun aLegacyFloatValueMigratesToACleanDouble() {
        val prefs = InMemorySharedPreferences()
        // What an older build left behind for an entered "30": 0.03f.
        prefs.edit().putFloat("kcal_per_step", 0.03f).apply()
        val store = ThemeSettingsStore(testContext(prefs))

        assertEquals(0.03, store.getKcalPerStep(), 0.0)
        assertEquals(30.0, store.getKcalPerStep() * 1000, 0.0)
    }

    @Test
    fun migrationRewritesTheDoubleKeyAndKeepsTheLegacyFloatKey() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putFloat("kcal_per_step", 0.03f).apply()
        val store = ThemeSettingsStore(testContext(prefs))

        store.getKcalPerStep()

        assertTrue(prefs.contains("kcal_per_step_double"))
        assertEquals(0.03, Double.fromBits(prefs.getLong("kcal_per_step_double", 0L)), 0.0)
        // A downgrade must still find its Float value.
        assertTrue(prefs.contains("kcal_per_step"))
    }

    @Test
    fun savingKeepsTheLegacyFloatKeyInSync() {
        val prefs = InMemorySharedPreferences()
        val store = ThemeSettingsStore(testContext(prefs))

        store.saveKcalPerStep(0.042)

        assertEquals(0.042f, prefs.getFloat("kcal_per_step", 0f), 0f)
        assertEquals(0.042, store.getKcalPerStep(), 0.0)
    }

    @Test
    fun theDoubleValueWinsOverAStaleLegacyFloat() {
        val prefs = InMemorySharedPreferences()
        prefs.edit()
            .putFloat("kcal_per_step", 0.03f)
            .putLong("kcal_per_step_double", (0.045).toRawBits())
            .apply()
        val store = ThemeSettingsStore(testContext(prefs))

        assertEquals(0.045, store.getKcalPerStep(), 0.0)
    }

    @Test
    fun anUnsetValueFallsBackToTheDocumentedDefault() {
        val store = ThemeSettingsStore(testContext(InMemorySharedPreferences()))

        assertEquals(0.035, store.getKcalPerStep(), 0.0)
        assertEquals(35.0, store.getKcalPerStep() * 1000, 0.0)
    }

    @Test
    fun cleaningRoundsFloatNoiseAwayAtFloatPrecision() {
        // Every one of these is a Float whose Double widening carries conversion noise.
        assertEquals(0.03, ThemeSettingsStore.cleanFloatPrecision(0.03f), 0.0)
        assertEquals(0.035, ThemeSettingsStore.cleanFloatPrecision(0.035f), 0.0)
        assertEquals(0.04, ThemeSettingsStore.cleanFloatPrecision(0.04f), 0.0)
        assertEquals(0.0325, ThemeSettingsStore.cleanFloatPrecision(0.0325f), 0.0)
        assertEquals(0.0456, ThemeSettingsStore.cleanFloatPrecision(0.0456f), 0.0)
        // The defect value itself, as it was stored.
        assertEquals(29.999999329447746, 0.03f.toDouble() * 1000, 0.0)
        assertEquals(30.0, ThemeSettingsStore.cleanFloatPrecision(0.03f) * 1000, 0.0)
    }
}
