package com.circumspace.contactstr.data.persistence

import android.content.Context
import com.circumspace.contactstr.security.SecureBlob
import java.io.File

/**
 * Persists the signed-in session, encrypted at rest via [SecureBlob], so sign-in survives restarts.
 *
 * Stored value is either a local hex private key, or an external-signer marker of the form
 * `ext|<pubKeyHex>|<signerPackage>` (Amber / NIP-55 — no private key held by the app).
 */
class KeyVault(context: Context) {
    private val file = File(context.filesDir, "identity.bin")

    fun save(value: String) {
        file.writeBytes(SecureBlob.seal(value.toByteArray(Charsets.UTF_8)))
    }

    fun load(): String? =
        if (file.exists()) {
            runCatching { String(SecureBlob.open(file.readBytes()), Charsets.UTF_8) }.getOrNull()
        } else {
            null
        }

    fun clear() {
        file.delete()
    }
}
