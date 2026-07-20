package com.circumspace.contactstr.domain

import java.util.UUID

/**
 * A contact record. Will grow to map losslessly to/from vCard.
 *
 * [photoUri] is a *local* content/file URI — a transient, device-only preview that does not sync
 * meaningfully across devices. [photo] is the durable, cross-device photo: an encrypted blob on
 * Blossom whose descriptor rides inside the already-encrypted contact event. When both are set,
 * [photo] is canonical; [photoUri] lingers only as a fast local preview until display switches over.
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
    /** Encrypted Blossom-hosted photo; canonical and cross-device when present. */
    val photo: ContactPhoto? = null,
    /** Pinned to the Favorites section at the top of the list. */
    val favorite: Boolean = false,
    /**
     * Last local edit time (epoch seconds) — the conflict-resolution clock for sync. A remote copy
     * is applied only if its [updatedAt] is newer, so two coexisting versions on different relays
     * converge to the latest edit instead of flip-flopping. 0 for legacy records (pre-dates this).
     */
    val updatedAt: Long = 0L,
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
