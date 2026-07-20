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
    /** vCard CATEGORIES — free-form groups like "family" or "work". Stored lowercase-insensitively. */
    val categories: List<String> = emptyList(),
    /**
     * vCard BDAY. Stored as a raw date string to preserve year-less birthdays: either a full
     * "YYYY-MM-DD" or a year-less "--MM-DD". Null when unset. Both forms are accepted by
     * ContactsContract's birthday Event and by our own birthday calendar.
     */
    val birthday: String? = null,
) {
    /**
     * Categories used for filtering/highlighting: the stored vCard categories plus the derived
     * [CATEGORY_NOSTR] when a Nostr identity is linked. Derived (not stored) so it can never go
     * stale and doesn't pollute exported vCards.
     */
    val effectiveCategories: Set<String>
        get() = buildSet {
            categories.forEach { c -> c.trim().lowercase().takeIf { it.isNotEmpty() }?.let { add(it) } }
            if (nostr.isNotBlank()) add(CATEGORY_NOSTR)
        }

    val initials: String
        get() = displayName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }

    companion object {
        /** Derived category marking contacts with a linked Nostr identity. */
        const val CATEGORY_NOSTR = "nostr"
    }
}
