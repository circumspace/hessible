package com.circumspace.contactstr.data.persistence

import android.content.Context
import com.circumspace.contactstr.security.SecureBlob
import org.json.JSONObject
import java.io.File

/**
 * Durable, per-owner record of deleted contacts: `contactId → deletion timestamp` (epoch seconds).
 *
 * NIP-09 (kind-5) deletion is advisory — many relays keep serving the original kind-30078 event
 * forever — so the client must remember its own deletions to suppress a re-arriving "ghost". This
 * store outlives the outbox (which drops a delete-op as soon as one relay acks it) so a deletion
 * sticks across restarts, relay changes, and reinstalls. Sealed with [SecureBlob] like the other
 * caches; scoped per owner pubkey since contact ids are owner-local.
 */
class TombstoneStore(private val context: Context) {
    private fun file(owner: String) = File(context.filesDir, "tombstones_$owner.bin")

    fun load(owner: String): Map<String, Long> {
        val f = file(owner)
        if (!f.exists()) return emptyMap()
        return runCatching {
            val o = JSONObject(String(SecureBlob.open(f.readBytes()), Charsets.UTF_8))
            buildMap { o.keys().forEach { k -> put(k, o.optLong(k)) } }
        }.getOrDefault(emptyMap())
    }

    fun save(owner: String, tombstones: Map<String, Long>) {
        val o = JSONObject()
        tombstones.forEach { (id, ts) -> o.put(id, ts) }
        file(owner).writeBytes(SecureBlob.seal(o.toString().toByteArray(Charsets.UTF_8)))
    }
}
