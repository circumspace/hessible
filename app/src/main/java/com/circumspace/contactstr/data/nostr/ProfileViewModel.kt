package com.circumspace.contactstr.data.nostr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.circumspace.contactstr.data.PROFILE_RELAYS
import com.circumspace.contactstr.data.SEARCH_RELAYS
import com.circumspace.contactstr.domain.NostrProfile
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Searches NIP-50 relays for profiles by name and fetches kind-0 metadata by pubkey, with an
 * in-memory cache keyed by pubkey-hex. Used to autocomplete the Nostr field and to enrich contacts
 * (avatar / nip05 / website) non-destructively.
 *
 * Two pools: [searchPool] (NIP-50 search relays) answers name search; [metaPool] (general relays)
 * answers author-based metadata fetches. Search-index relays like search.nos.today don't serve
 * normal `authors` filters, so metadata must come from the general relays where kind-0 lives.
 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val searchPool = RelayPool(SEARCH_RELAYS).also { it.connect() }
    private val metaPool = RelayPool(PROFILE_RELAYS).also { it.connect() }

    private val _cache = MutableStateFlow<Map<String, NostrProfile>>(emptyMap())
    val cache: StateFlow<Map<String, NostrProfile>> = _cache.asStateFlow()

    /** The signed-in user's own kind-0 profile (for their avatar), once fetched. */
    private val _ownerProfile = MutableStateFlow<NostrProfile?>(null)
    val ownerProfile: StateFlow<NostrProfile?> = _ownerProfile.asStateFlow()

    /** Pubkey-hexes the signed-in user follows (kind-3) — the first-degree Web of Trust. */
    @Volatile
    private var follows: Set<String> = emptySet()
    private var ownerPubkey: String? = null

    /** Pubkey-hexes with an in-flight metadata fetch, so repeated prefetches don't re-query them. */
    private val inFlightProfiles = mutableSetOf<String>()

    /**
     * Sets the active user and fetches their kind-3 follow list (public, no signing) to use as a
     * Web-of-Trust ranking signal in [search]. Idempotent per owner; clears on sign-out (null).
     */
    fun setOwner(pubKeyHex: String?) {
        if (pubKeyHex == ownerPubkey) return
        ownerPubkey = pubKeyHex
        follows = emptySet()
        _ownerProfile.value = null
        val owner = pubKeyHex ?: return

        // Fetch the owner's own kind-0 metadata for their avatar.
        viewModelScope.launch {
            val filter = JSONObject()
                .put("kinds", JSONArray().put(0))
                .put("authors", JSONArray().put(owner))
                .put("limit", 1)
            query(filter, metaPool).mapNotNull { NostrProfile.fromEvent(it) }.firstOrNull()?.let {
                _ownerProfile.value = it
                cacheAll(listOf(it))
            }
        }

        // Fetch the kind-3 follow list for Web-of-Trust search ranking.
        viewModelScope.launch {
            val filter = JSONObject()
                .put("kinds", JSONArray().put(3))
                .put("authors", JSONArray().put(owner))
                .put("limit", 1)
            // Newest kind-3 wins; p-tags are the followed pubkeys.
            val latest = query(filter, metaPool).maxByOrNull { it.optLong("created_at") } ?: return@launch
            val tags = latest.optJSONArray("tags") ?: return@launch
            follows = buildSet {
                for (i in 0 until tags.length()) {
                    val tag = tags.optJSONArray(i) ?: continue
                    if (tag.optString(0) == "p") tag.optString(1).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
    }

    /** NIP-50 search by display name. Drops bridge accounts and ranks by Web of Trust + quality. */
    suspend fun search(query: String): List<NostrProfile> {
        // Strip surrounding quotes a user might add to group words — the relay does its own tokenizing.
        val q = query.trim().trim('"', '\'').trim()
        val filter = JSONObject()
            .put("kinds", JSONArray().put(0))
            .put("search", q)
            .put("limit", 30)
        val profiles = query(filter, searchPool)
            .mapNotNull { NostrProfile.fromEvent(it) }
            .distinctBy { it.pubKeyHex }
            .filterNot { it.isBridged() }
            .sortedByDescending { it.rank(q) }
            .take(8)
        cacheAll(profiles)
        return profiles
    }

    /** Higher is better: WoT membership dominates, then name-match quality, then completeness. */
    private fun NostrProfile.rank(query: String): Int {
        val q = query.trim().lowercase()
        val n = name.trim().lowercase()
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        var score = 0
        if (pubKeyHex in follows) score += 1000          // first-degree Web of Trust
        when {
            n == q -> score += 100                        // exact full-name match
            n.startsWith(q) -> score += 60                // prefix match on the whole query
            n.contains(q) -> score += 40                  // whole query appears as a substring
        }
        // Multi-word: reward names containing each query token (handles word order / spacing).
        if (tokens.size > 1) {
            val hits = tokens.count { n.contains(it) }
            score += hits * 20
            if (hits == tokens.size) score += 40         // all words present
        }
        if (nip05.isNotBlank()) score += 15               // claims a NIP-05 identifier
        if (picture.isNotBlank()) score += 5
        if (about.isNotBlank()) score += 3
        return score
    }

    /** Fetch & cache the profile for an npub/nprofile if not already cached. */
    fun ensureProfile(nostrId: String) = ensureProfiles(listOf(nostrId))

    /**
     * Fetch & cache profiles for many npub/nprofiles in a *single* multi-author REQ. The contact
     * list calls this once per list change instead of firing one query per contact — avoiding a
     * fan-out of concurrent subscriptions (and their per-frame parsing) at startup.
     */
    fun ensureProfiles(nostrIds: Collection<String>) {
        val cached = _cache.value
        val hexes = nostrIds
            .mapNotNull { decodePublicKeyAsHexOrNull(it.trim()) }
            .distinct()
            .filterNot { cached.containsKey(it) || it in inFlightProfiles }
        if (hexes.isEmpty()) return
        inFlightProfiles += hexes
        viewModelScope.launch {
            try {
                val authors = JSONArray().apply { hexes.forEach { put(it) } }
                val filter = JSONObject()
                    .put("kinds", JSONArray().put(0))
                    .put("authors", authors)
                    .put("limit", hexes.size)
                val profiles = query(filter, metaPool)
                    .mapNotNull { NostrProfile.fromEvent(it) }
                    .distinctBy { it.pubKeyHex }
                cacheAll(profiles)
            } finally {
                inFlightProfiles -= hexes.toSet()
            }
        }
    }

    /** Cached profile for an npub/nprofile, decoding to pubkey-hex. */
    fun lookup(cacheMap: Map<String, NostrProfile>, nostrId: String): NostrProfile? {
        val pubKeyHex = decodePublicKeyAsHexOrNull(nostrId.trim()) ?: return null
        return cacheMap[pubKeyHex]
    }

    /**
     * Heuristically detects Bluesky / Mastodon / RSS bridge accounts (e.g. via mostr.pub,
     * momostr, brid.gy, atomstr) — these mirror non-Nostr identities and pollute name search.
     */
    private fun NostrProfile.isBridged(): Boolean {
        val domain = nip05.substringAfterLast('@', "").lowercase()
        if (BRIDGE_DOMAINS.any { domain == it || domain.endsWith(".$it") }) return true
        val haystack = "${name.lowercase()} ${about.lowercase()}"
        return BRIDGE_MARKERS.any { it in haystack }
    }

    private fun cacheAll(profiles: List<NostrProfile>) {
        if (profiles.isEmpty()) return
        _cache.update { current -> current + profiles.associateBy { it.pubKeyHex } }
    }

    /**
     * Sends a REQ, collects matching EVENTs, then CLOSEs. Completion uses a grace window rather than
     * a relay count: once the *first* relay signals EOSE we wait [GRACE_MS] for stragglers, then
     * finish. This avoids the race where counting only currently-connected relays truncated results
     * when a relay was still mid-handshake at search time (the "sometimes works" flakiness). A hard
     * [TIMEOUT_MS] cap covers the case where no relay ever responds.
     */
    private suspend fun query(filter: JSONObject, pool: RelayPool): List<JSONObject> {
        val sub = "ps-${UUID.randomUUID()}"
        val out = mutableListOf<JSONObject>()
        val done = CompletableDeferred<Unit>()

        val collector = viewModelScope.launch(Dispatchers.Default) {
            var graceStarted = false
            pool.incoming.collect { text ->
                val arr = runCatching { JSONArray(text) }.getOrNull() ?: return@collect
                if (arr.optString(1) != sub) return@collect
                when (arr.optString(0)) {
                    "EVENT" -> arr.optJSONObject(2)?.let { out.add(it) }
                    "EOSE", "CLOSED" -> if (!graceStarted) {
                        graceStarted = true
                        launch { delay(GRACE_MS); done.complete(Unit) }
                    }
                }
            }
        }
        // Subscribe first (the collector above is launched), then yield so it is actively
        // collecting before the REQ goes out — otherwise replay=0 frames could be dropped.
        yield()
        pool.send(JSONArray().put("REQ").put(sub).put(filter).toString())
        withTimeoutOrNull(TIMEOUT_MS) { done.await() }
        collector.cancel()
        pool.send(JSONArray().put("CLOSE").put(sub).toString())
        return out.toList()
    }

    override fun onCleared() {
        searchPool.close()
        metaPool.close()
        super.onCleared()
    }

    private companion object {
        /** After the first relay's EOSE, wait this long for slower relays before finishing. */
        const val GRACE_MS = 800L

        /** Hard cap if no relay ever responds. */
        const val TIMEOUT_MS = 5000L

        /** NIP-05 domains operated by protocol bridges that mirror non-Nostr accounts. */
        val BRIDGE_DOMAINS = setOf(
            "mostr.pub",        // Mastodon/ActivityPub bridge
            "momostr.pink",     // Mastodon bridge
            "brid.gy",          // Bluesky bridge (Bridgy Fed)
            "bsky.brid.gy",
            "atomstr.data.haus", // RSS/Atom feed bridge
            "rsslay.nostr.moe",  // RSS bridge
        )

        /** Name/about markers that flag bridged or feed-mirror accounts. */
        val BRIDGE_MARKERS = listOf(
            "(rss feed)",
            ".bsky.social",
            "bridged from",
            "activitypub",
        )
    }
}
