package com.circumspace.contactstr.domain

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import org.json.JSONObject

/**
 * A Nostr profile (kind-0 metadata) used to enrich a contact: avatar, name, nip05, etc.
 * This is public, fetched data — distinct from a contact's own (private) fields.
 */
data class NostrProfile(
    val pubKeyHex: String,
    val npub: String,
    val name: String = "",
    val nip05: String = "",
    val picture: String = "",
    val about: String = "",
    val website: String = "",
    val lud16: String = "",
) {
    companion object {
        /** Parse a kind-0 event JSON object into a profile, or null if it has no pubkey. */
        fun fromEvent(event: JSONObject): NostrProfile? {
            val pubKeyHex = event.optString("pubkey").ifBlank { return null }
            val meta = runCatching { JSONObject(event.optString("content")) }.getOrNull() ?: JSONObject()
            return NostrProfile(
                pubKeyHex = pubKeyHex,
                npub = runCatching { pubKeyHex.hexToByteArray().toNpub() }.getOrDefault(""),
                name = meta.optString("display_name").ifBlank { meta.optString("name") },
                nip05 = meta.optString("nip05"),
                picture = meta.optString("picture"),
                about = meta.optString("about"),
                website = meta.optString("website"),
                lud16 = meta.optString("lud16"),
            )
        }
    }
}
