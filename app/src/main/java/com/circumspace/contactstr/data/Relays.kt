package com.circumspace.contactstr.data

/**
 * Relay roles, kept distinct per Nostr conventions. Mixing them causes silent misses (e.g. a
 * search-only index returns nothing for an author filter). Four roles:
 *
 *  - [DATA_RELAYS]    — read/write the user's own encrypted kind-30078 contacts (their data store).
 *  - [SEARCH_RELAYS]  — NIP-50 name search only (few relays implement `search`).
 *  - [PROFILE_RELAYS] — author-based kind-0 / kind-3 / kind-10002 metadata reads (avatars, follows).
 *  - Media uploads    — NOT relays: binary blobs belong on Blossom / NIP-96 hosts (future).
 *
 * All static for now; user-managed relay lists (NIP-65) arrive with relay management in Settings.
 */

/** Read/write the user's own encrypted contact events. Shown read-only in the Settings sync panel. */
val DATA_RELAYS: List<String> = listOf(
    "wss://relay.damus.io",
    "wss://nos.lol",
    "wss://relay.primal.net",
    "wss://nostrelay.circum.space",
)

/**
 * NIP-50-capable relays used for profile name search. Only relays that actually implement the
 * `search` filter belong here — and search indexes (e.g. search.nos.today) often don't serve
 * normal `authors` reads, which is why metadata uses [PROFILE_RELAYS] instead.
 */
// NOTE: do NOT add relay.nostr.band — it no longer resolves. More search relays TBD.
val SEARCH_RELAYS: List<String> = listOf(
    "wss://relay.noswhere.com",
    "wss://search.nos.today",
)

/**
 * Relays for author-based metadata reads (contact avatars, the owner's profile, follow lists).
 * Includes purplepag.es, a purpose-built aggregator that mirrors kind-0 / kind-10002 for most of
 * the network, plus large general relays for redundancy.
 */
val PROFILE_RELAYS: List<String> = listOf(
    "wss://purplepag.es",
    "wss://relay.damus.io",
    "wss://relay.primal.net",
    "wss://nos.lol",
    // The app's home relay — where contactstr users publish, so it hosts profiles the big
    // aggregators may not have yet. Keep it in the profile-read set.
    "wss://nostrelay.circum.space",
)

/** Citrine and similar on-device relays listen here by default (cleartext loopback). */
const val LOCAL_RELAY_URL = "ws://127.0.0.1:4869"

/**
 * How long a relay is expected to retain events — a user-set hint, not enforced. Free relays
 * commonly prune old notes; paid and self-hosted relays are durable; a local relay is an
 * on-device backup. Drives the "your data may be pruned" nudge and helps the user reason about
 * where their contacts actually persist.
 */
enum class Durability { FREE, PAID, SELF_HOSTED, LOCAL }

/** A user-managed relay entry for the contact data store. */
data class RelayConfig(
    val url: String,
    val enabled: Boolean = true,
    val durability: Durability = Durability.FREE,
) {
    val isLocal: Boolean get() = durability == Durability.LOCAL
}

/** Seed list on first run: the known-stable defaults, all marked FREE until the user says otherwise. */
val DEFAULT_RELAY_CONFIGS: List<RelayConfig> = DATA_RELAYS.map { RelayConfig(it, enabled = true) }
