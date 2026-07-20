package com.circumspace.contactstr.domain

import java.util.UUID

/**
 * A contact record. Will grow to map losslessly to/from vCard.
 *
 * [photoUri] is a local content/file URI for now. When encrypted relay storage lands, the photo
 * will be encrypted client-side, uploaded to a Blossom / NIP-96 host, and this field will hold the
 * blob URL + decryption key (both inside the already-encrypted contact event).
 */
data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val website: String = "",
    /** A Nostr identity (npub or nprofile) associated with this contact. */
    val nostr: String = "",
    val note: String = "",
    val photoUri: String? = null,
    /** Pinned to the Favorites section at the top of the list. */
    val favorite: Boolean = false,
) {
    val initials: String
        get() = displayName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}
