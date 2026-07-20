package com.circumspace.contactstr.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM for contact-photo blobs. The key + nonce are generated per photo and stored (hex)
 * inside the NIP-44-encrypted contact event, so the blob on Blossom is opaque to anyone else.
 *
 * A fresh random key+nonce per photo means the (nonce, key) pair is never reused — the standard
 * GCM safety requirement — without any counter state to track.
 */
object ImageCrypto {
    private const val KEY_BITS = 256
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    /** GCM ciphertext plus the hex key + nonce that decrypt it (ready to store in the descriptor). */
    data class Encrypted(val keyHex: String, val nonceHex: String, val ciphertext: ByteArray)

    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray): Encrypted {
        val key = ByteArray(KEY_BITS / 8).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        }
        return Encrypted(key.toHex(), nonce.toHex(), cipher.doFinal(plaintext))
    }

    /** Decrypt GCM [ciphertext] with hex [keyHex] / [nonceHex]. Throws on a bad tag (tampering). */
    fun decrypt(ciphertext: ByteArray, keyHex: String, nonceHex: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyHex.hexToBytes(), "AES"),
                GCMParameterSpec(TAG_BITS, nonceHex.hexToBytes()),
            )
        }
        return cipher.doFinal(ciphertext)
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { ((this[it * 2].digit() shl 4) or this[it * 2 + 1].digit()).toByte() }

    private fun Char.digit(): Int = Character.digit(this, 16)
}
