package com.circumspace.contactstr.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * At-rest encryption backed by a hardware-bound key in the Android Keystore.
 *
 * The AES-256-GCM key never leaves the Keystore (we only ever pass plaintext/ciphertext through
 * a [Cipher]). Output layout is `[12-byte IV][ciphertext+GCM tag]`.
 */
object SecureBlob {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "contactstr.master.v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    /**
     * The Keystore key handle is expensive to re-resolve (`KeyStore.load` + `getEntry`), and
     * seal/open run for every persisted blob (identity, contacts, outbox, relays, tombstones).
     * Cache it for the process lifetime; the key material itself never leaves the Keystore.
     */
    @Volatile
    private var cachedKey: SecretKey? = null

    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(this) {
            cachedKey ?: loadOrCreateKey().also { cachedKey = it }
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun seal(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    fun open(blob: ByteArray): ByteArray {
        require(blob.size > IV_LENGTH) { "blob too short" }
        val iv = blob.copyOfRange(0, IV_LENGTH)
        val ciphertext = blob.copyOfRange(IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
