package com.example.vocalorie.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * There is no AndroidKeyStore on the JVM, so every decrypt here fails the same way it would on a
 * device whose KeyStore entry disappeared after a restore. That makes the read-failure path — the
 * one that used to delete the user's key — testable without a device or a real key.
 *
 * Still device-only: that a key written by [OpenAiApiKeyStore.save] decrypts back, since both
 * halves need real KeyStore and `android.util.Base64`.
 */
class OpenAiApiKeyStoreTest {
    @Test
    fun unreadableKeyKeepsTheStoredCiphertext() {
        val prefs = storedKeyPrefs()
        val store = OpenAiApiKeyStore(testContext(prefs))

        assertNull(store.get())

        assertTrue(prefs.contains("ciphertext"))
        assertTrue(prefs.contains("iv"))
        assertTrue(prefs.contains("last4"))
        assertTrue(store.hasSavedKey())
    }

    @Test
    fun unreadableKeyIsDistinguishableFromNoKey() {
        val storedButUnreadable = OpenAiApiKeyStore(testContext(storedKeyPrefs()))
        val notConfigured = OpenAiApiKeyStore(testContext(InMemorySharedPreferences()))

        storedButUnreadable.get()

        assertEquals(SecretKeyState.UNREADABLE, storedButUnreadable.keyState())
        assertEquals("Saved key could not be read. Re-enter it.", storedButUnreadable.displayLabel())

        assertEquals(SecretKeyState.NOT_CONFIGURED, notConfigured.keyState())
        assertNull(notConfigured.displayLabel())
    }

    @Test
    fun keyIsReportedAsSavedUntilAReadActuallyFails() {
        val prefs = storedKeyPrefs()
        val store = OpenAiApiKeyStore(testContext(prefs))

        assertEquals(SecretKeyState.SAVED, store.keyState())
        assertEquals("Saved key ending in 1234", store.displayLabel())
    }

    @Test
    fun clearingRemovesEveryStoredFieldIncludingTheReadFailureFlag() {
        val prefs = storedKeyPrefs()
        val store = OpenAiApiKeyStore(testContext(prefs))

        store.get()
        store.clear()

        assertFalse(prefs.contains("iv"))
        assertFalse(prefs.contains("ciphertext"))
        assertFalse(prefs.contains("last4"))
        assertFalse(prefs.contains("read_failed"))
        assertEquals(SecretKeyState.NOT_CONFIGURED, store.keyState())
        assertNull(store.displayLabel())
    }

    @Test
    fun savingRejectsBlankKeys() {
        val store = OpenAiApiKeyStore(testContext(InMemorySharedPreferences()))

        val failure = runCatching { store.save("   ") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    private fun storedKeyPrefs(): InMemorySharedPreferences = InMemorySharedPreferences().apply {
        edit()
            .putString("iv", "aXYtYnl0ZXM=")
            .putString("ciphertext", "Y2lwaGVydGV4dA==")
            .putString("last4", "1234")
            .apply()
    }
}
