package com.example.vocalorie.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    private fun testContext(prefs: SharedPreferences): Context = object : Application() {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (values[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = (values[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = (values[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = (values[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (values[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { pending[requireKey(key)] = value }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { pending[requireKey(key)] = values }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { pending[requireKey(key)] = value }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { pending[requireKey(key)] = value }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { pending[requireKey(key)] = value }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { pending[requireKey(key)] = value }

        override fun remove(key: String?): SharedPreferences.Editor = apply { pending[requireKey(key)] = REMOVED }

        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

        override fun commit(): Boolean {
            if (clearRequested) values.clear()
            pending.forEach { (key, value) ->
                if (value === REMOVED) values.remove(key) else values[key] = value
            }
            pending.clear()
            clearRequested = false
            return true
        }

        override fun apply() {
            commit()
        }

        private fun requireKey(key: String?): String = requireNotNull(key) { "Preference key cannot be null." }
    }

    private companion object {
        private val REMOVED = Any()
    }
}
