package com.circumspace.contactstr.data.nostr

import com.circumspace.contactstr.crypto.NostrIdentity
import com.circumspace.contactstr.data.ContactJson
import com.circumspace.contactstr.data.persistence.OutboxOp
import com.circumspace.contactstr.data.persistence.OutboxStore
import com.circumspace.contactstr.domain.Contact
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Syncs contacts with relays:
 *  - publishes each contact as a NIP-44-encrypted **kind-30078** addressable event (d-tag per contact),
 *  - subscribes for the signed-in author's 30078 events, decrypts them, and feeds them back, and
 *  - publishes / applies **NIP-09 (kind-5)** deletions.
 *
 * Publishing goes through a durable **outbox of intents** ([OutboxStore]) rather than signing
 * inline: an edit or delete is enqueued as plaintext, then a single-flight [drain] signs (via the
 * possibly-remote Amber signer) and sends it, keeping the entry until the relay's OK ack. If
 * signing fails or the signer is unavailable (a real hazard with external signers), the intent
 * stays queued and is retried on the next relay connect or app-foreground [retry] — so a write is
 * never silently lost the way inline signing could lose it.
 */
class SyncManager(
    private val relays: List<String>,
    private val scope: CoroutineScope,
    private val outboxStore: OutboxStore,
    private val onRemoteContact: (Contact) -> Unit,
    private val onRemoteDelete: (String) -> Unit,
) {
    private val subId = "contactstr"
    private val dTagPrefix = "circumspace.contactstr/contact/"
    private val appKind = 30078
    private val deletionKind = 5

    private var pool: RelayPool? = null
    private var identity: NostrIdentity? = null

    /** Pending publish intents, keyed by contact id (latest op per contact wins). */
    private val outbox = LinkedHashMap<String, OutboxOp>()
    private val outboxMutex = Mutex()      // serializes drain() runs
    private val persistMutex = Mutex()     // serializes outbox file writes (mass-delete bursts)
    /**
     * contact id -> signed wire frame + event id, cached once signed so retries needn't re-sign.
     * Concurrent: written by drain() (Default) and read/removed by onOk() (relay thread) and
     * enqueue() (main) — so it must be thread-safe, or a mass-delete race crashes the app.
     */
    private val signedCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    /**
     * contact id -> latest NIP-09 deletion timestamp (epoch seconds). A kind-30078 event for a
     * tombstoned contact is ignored unless it was created strictly after the deletion — this makes
     * deletions stick even when a relay keeps serving the old event or ignores NIP-09.
     */
    private val tombstones = HashMap<String, Long>()

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun start(id: NostrIdentity) {
        identity = id
        // Restore intents that never got an OK ack (offline edits, failed signer, process death).
        // Deletion intents re-arm their tombstones so a relay's stale copy can't win the race.
        val restored = outboxStore.load()
        synchronized(outbox) {
            restored.forEach { op ->
                outbox[op.contactId] = op
                if (op.type == OutboxOp.Type.DELETE) {
                    tombstones[op.contactId] = maxOf(tombstones[op.contactId] ?: 0L, op.createdAt)
                }
            }
        }

        val p = RelayPool(relays).also { pool = it }
        _state.value = SyncState(
            relays = relays.map { RelayConn(it, false) },
            syncing = true,
            pendingWrites = outbox.size,
        )

        scope.launch {
            p.connected.collect { set ->
                _state.update { s -> s.copy(relays = relays.map { RelayConn(it, it in set) }) }
            }
        }
        // Parse + decrypt inbound frames off the main thread so a startup sync burst can't jank the list.
        scope.launch(Dispatchers.Default) { p.incoming.collect { handle(it, id) } }

        p.setOnConnect {
            p.send(reqMessage(id.pubKeyHex))
            retry() // flush the outbox to the freshly-connected relay
        }
        p.connect()
    }

    /** Queue a contact upsert for publishing (durable; signed + sent by [drain]). */
    fun publishContact(contact: Contact) {
        enqueue(OutboxOp(contact.id, OutboxOp.Type.UPSERT, nowSec(), contact))
    }

    /** Queue a NIP-09 deletion for [contactId] (durable). Tombstones immediately. */
    fun publishDeletion(contactId: String) {
        val now = nowSec()
        synchronized(tombstones) { tombstones[contactId] = maxOf(tombstones[contactId] ?: 0L, now) }
        enqueue(OutboxOp(contactId, OutboxOp.Type.DELETE, now, contact = null))
    }

    /** Re-attempt signing + sending everything in the outbox — call on connect and app-foreground. */
    fun retry() {
        scope.launch { drain() }
    }

    /**
     * Re-establish a full sync — call on app-foreground. Revives dropped relay sockets (each
     * re-subscribes on reconnect) and re-issues the subscription on already-open ones, so contacts
     * pushed by another device while we were backgrounded/asleep get pulled in. Also flushes the
     * outbox. Without this, sync only ever ran from the initial cold-start connect.
     */
    fun resync() {
        val id = identity ?: return
        val p = pool ?: return
        p.reconnect()
        p.send(reqMessage(id.pubKeyHex)) // re-pull on relays that were already open
        _state.update { it.copy(syncing = true) }
        retry()
    }

    fun stop() {
        pool?.close()
        pool = null
        // In-memory only — the persisted outbox survives and reloads on the next start().
        synchronized(outbox) { outbox.clear() }
        signedCache.clear()
        synchronized(tombstones) { tombstones.clear() }
        _state.value = SyncState()
    }

    // ── outbox ─────────────────────────────────────────────────────────────────

    private fun enqueue(op: OutboxOp) {
        synchronized(outbox) {
            outbox[op.contactId] = op
            signedCache.remove(op.contactId) // intent changed → any cached signature is stale
        }
        persistOutbox()
        _state.update { it.copy(pendingWrites = outbox.size) }
        retry()
    }

    private fun persistOutbox() {
        // Serialize writes and snapshot inside the lock so a burst of enqueues can't write the
        // same file concurrently, and the last write reflects the latest state.
        scope.launch(Dispatchers.IO) {
            persistMutex.withLock { outboxStore.save(synchronized(outbox) { outbox.values.toList() }) }
        }
    }

    /** Single-flight: sign (if needed) and send every queued op. Ops that fail to sign stay queued. */
    private suspend fun drain() {
        val id = identity ?: return
        val p = pool ?: return
        if (!outboxMutex.tryLock()) return // a drain is already running; it'll cover new ops
        try {
            val ops = synchronized(outbox) { outbox.values.toList() }
            for (op in ops) {
                val cached = signedCache[op.contactId]
                val frame = cached?.first ?: run {
                    // Sign lazily; a signer failure (Amber unavailable) just leaves the op queued.
                    val event = runCatching { sign(id, op) }.getOrNull() ?: return@run null
                    val f = """["EVENT",${event.toJson()}]"""
                    signedCache[op.contactId] = f to event.id
                    f
                }
                if (frame != null) p.send(frame)
            }
        } finally {
            outboxMutex.unlock()
        }
    }

    private suspend fun sign(id: NostrIdentity, op: OutboxOp): Event = when (op.type) {
        OutboxOp.Type.UPSERT -> {
            val contact = op.contact ?: error("upsert op without contact")
            val ciphertext = id.signer.nip44Encrypt(ContactJson.toJsonString(contact), id.pubKeyHex)
            val tags = arrayOf(arrayOf("d", dTagPrefix + contact.id), arrayOf("client", "contactstr"))
            id.signer.sign(op.createdAt, appKind, tags, ciphertext)
        }
        OutboxOp.Type.DELETE -> {
            val coordinate = "$appKind:${id.pubKeyHex}:$dTagPrefix${op.contactId}"
            val tags = arrayOf(arrayOf("a", coordinate), arrayOf("k", appKind.toString()))
            id.signer.sign(op.createdAt, deletionKind, tags, "")
        }
    }

    private fun handle(text: String, id: NostrIdentity) {
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return
        when (arr.optString(0)) {
            "EVENT" -> handleEvent(arr.optJSONObject(2) ?: return, id)
            "EOSE" -> _state.update { it.copy(syncing = false, lastSyncAtMs = System.currentTimeMillis()) }
            "OK" -> {
                val eventId = arr.optString(1)
                if (arr.optBoolean(2)) onOk(eventId)
            }
        }
    }

    /** An OK for [eventId]: find the outbox op whose cached signature matches, and clear it. */
    private fun onOk(eventId: String) {
        val contactId = signedCache.entries.firstOrNull { it.value.second == eventId }?.key ?: return
        val removed = synchronized(outbox) { outbox.remove(contactId) != null }
        signedCache.remove(contactId)
        if (removed) {
            persistOutbox()
            _state.update { it.copy(published = it.published + 1, pendingWrites = outbox.size) }
        }
    }

    private fun handleEvent(obj: JSONObject, id: NostrIdentity) {
        when (obj.optInt("kind")) {
            appKind -> {
                val createdAt = obj.optLong("created_at")
                scope.launch(Dispatchers.Default) {
                    val plain = runCatching { id.signer.nip44Decrypt(obj.optString("content"), id.pubKeyHex) }
                        .getOrNull() ?: return@launch
                    ContactJson.fromJsonString(plain)?.let { contact ->
                        // Ignore events for a deleted contact unless re-created after the deletion.
                        val deletedAt = synchronized(tombstones) { tombstones[contact.id] }
                        if (deletedAt != null && createdAt <= deletedAt) return@launch
                        onRemoteContact(contact)
                        _state.update { it.copy(received = it.received + 1) }
                    }
                }
            }
            deletionKind -> {
                val createdAt = obj.optLong("created_at")
                val tags = obj.optJSONArray("tags") ?: return
                for (i in 0 until tags.length()) {
                    val tag = tags.optJSONArray(i) ?: continue
                    if (tag.optString(0) == "a" && tag.optString(1).contains(dTagPrefix)) {
                        val contactId = tag.optString(1).substringAfter(dTagPrefix)
                        if (contactId.isNotEmpty()) {
                            synchronized(tombstones) {
                                tombstones[contactId] = maxOf(tombstones[contactId] ?: 0L, createdAt)
                            }
                            onRemoteDelete(contactId)
                        }
                    }
                }
            }
        }
    }

    // Subscribe to both contact events and our own deletions, so resyncs honor past deletes.
    private fun reqMessage(pubkey: String): String =
        JSONArray()
            .put("REQ")
            .put(subId)
            .put(
                JSONObject()
                    .put("kinds", JSONArray().put(appKind).put(deletionKind))
                    .put("authors", JSONArray().put(pubkey)),
            )
            .toString()

    private fun nowSec() = System.currentTimeMillis() / 1000
}
