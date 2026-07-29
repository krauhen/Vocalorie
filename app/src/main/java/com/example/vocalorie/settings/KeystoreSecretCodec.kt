package com.example.vocalorie.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A Base64-encoded AES/GCM secret, split into the IV and the ciphertext as persisted. */
data class EncryptedSecret(val ivBase64: String, val ciphertextBase64: String)

/**
 * The single KeyStore-backed encryption implementation for every secret the app stores.
 *
 * Each secret store owns one instance with its own [alias]; nothing else about the scheme
 * (transformation, tag length, randomized IV) may differ between stores, because a change here
 * has to cover every stored secret at once.
 */
class KeystoreSecretCodec(private val alias: String) {

    /** Encrypts [value] with a fresh random IV under this codec's KeyStore alias. */
    fun encrypt(value: String): EncryptedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))

        return EncryptedSecret(
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
        )
    }

    /**
     * Decrypts a previously stored secret, or returns `null` when it cannot be read (for example
     * because the KeyStore entry is gone after a device restore).
     *
     * A `null` result means "unreadable", never "absent": callers must keep the stored ciphertext.
     */
    fun decrypt(ivBase64: String, ciphertextBase64: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivBase64, Base64.NO_WRAP)),
        )
        String(cipher.doFinal(Base64.decode(ciphertextBase64, Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
