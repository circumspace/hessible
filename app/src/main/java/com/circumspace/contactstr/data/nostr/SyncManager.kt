package com.circumspace.contactstr.data.nostr

import com.circumspace.contactstr.crypto.NostrIdentity
import com.circumspace.contactstr.data.ContactJson
import com.circumspace.contactstr.domain.Contact
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Syncs contacts with relays:
 *  - publishes each contact as a NIP-44-encrypted **kind-30078** addressable event (d-tag per contact),
 *  - subscribes for the signed-in author's 30078 events, decrypts them, and feeds them back, and
 *  - publishes / applies **NIP-09 (kind-5)** deletions.
 *
 * Writes are queued and (re)flushed whenever a relay connects, so saves made before a connection
 * is established (or while offline) still land. [state] drives the Settings sync panel.
 */
class SyncManager(
    private val relays: List<String>,
    private val scope: CoroutineScope,
    private val onRemoteContact: (Contact) -> Unit,
    private val onRemoteDelete: (String) -> Unit,
) {
    private val subId = "contactstr"
    private val dTagPrefix = "circumspace.contactstr/contact/"
    private val appKind = 30078
    private val deletionKind = 5

    private var pool: RelayPool? = null
    private var identity: NostrIdentity? = null

    /** event id -> wire frame, awaiting an OK ack. */
    private val pending = LinkedHashMap<String, String>()
    private val acked = HashSet<String>()

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
        val p = RelayPool(relays).also { pool = it }
        _state.value = SyncState(relays = relays.map { RelayConn(it, false) }, syncing = true)

        scope.launch {
            p.connected.collect { set ->
                _state.update { s -> s.copy(relays = relays.map { RelayConn(it, it in set) }) }
            }
        }
        scope.launch { p.incoming.collect { handle(it, id) } }

        p.setOnConnect {
            p.send(reqMessage(id.pubKeyHex))
            pending.values.toList().forEach { frame -> p.send(frame) }
        }
        p.connect()
    }

    suspend fun publishContact(contact: Contact) {
        val id = identity ?: return
        val ciphertext = id.signer.nip44Encrypt(ContactJson.toJsonString(contact), id.pubKeyHex)
        val tags = arrayOf(arrayOf("d", dTagPrefix + contact.id), arrayOf("client", "contactstr"))
        val event: Event = id.signer.sign(nowSec(), appKind, tags, ciphertext)
        enqueueAndSend(event)
    }

    /** NIP-09 deletion of the addressable event for [contactId]. */
    suspend fun publishDeletion(contactId: String) {
        val id = identity ?: return
        val now = nowSec()
        // Tombstone immediately so a stale copy already in-flight from a relay can't re-add it.
        tombstones[contactId] = maxOf(tombstones[contactId] ?: 0L, now)
        val coordinate = "$appKind:${id.pubKeyHex}:$dTagPrefix$contactId"
        val tags = arrayOf(arrayOf("a", coordinate), arrayOf("k", appKind.toString()))
        val event: Event = id.signer.sign(now, deletionKind, tags, "")
        enqueueAndSend(event)
    }

    fun stop() {
        pool?.close()
        pool = null
        pending.clear()
        acked.clear()
        tombstones.clear()
        _state.value = SyncState()
    }

    private fun enqueueAndSend(event: Event) {
        val frame = """["EVENT",${event.toJson()}]"""
        pending[event.id] = frame
        _state.update { it.copy(pendingWrites = pending.size) }
        pool?.send(frame)
    }

    private fun handle(text: String, id: NostrIdentity) {
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return
        when (arr.optString(0)) {
            "EVENT" -> handleEvent(arr.optJSONObject(2) ?: return, id)
            "EOSE" -> _state.update { it.copy(syncing = false, lastSyncAtMs = System.currentTimeMillis()) }
            "OK" -> {
                val eventId = arr.optString(1)
                if (arr.optBoolean(2) && acked.add(eventId)) {
                    pending.remove(eventId)
                    _state.update { it.copy(published = it.published + 1, pendingWrites = pending.size) }
                }
            }
        }
    }

    private fun handleEvent(obj: JSONObject, id: NostrIdentity) {
        when (obj.optInt("kind")) {
            appKind -> {
                val createdAt = obj.optLong("created_at")
                scope.launch {
                    val plain = runCatching { id.signer.nip44Decrypt(obj.optString("content"), id.pubKeyHex) }
                        .getOrNull() ?: return@launch
                    ContactJson.fromJsonString(plain)?.let { contact ->
                        // Ignore events for a deleted contact unless re-created after the deletion.
                        val deletedAt = tombstones[contact.id]
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
                            tombstones[contactId] = maxOf(tombstones[contactId] ?: 0L, createdAt)
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
