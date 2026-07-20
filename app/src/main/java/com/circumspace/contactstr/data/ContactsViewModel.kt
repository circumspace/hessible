package com.circumspace.contactstr.data

import android.accounts.Account
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.circumspace.contactstr.crypto.NostrIdentity
import com.circumspace.contactstr.data.nostr.LocalRelayProbe
import com.circumspace.contactstr.data.nostr.SyncManager
import com.circumspace.contactstr.data.nostr.SyncState
import com.circumspace.contactstr.data.persistence.ContactStore
import com.circumspace.contactstr.data.persistence.RelayStore
import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.sync.ContactsContractHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Contacts for the signed-in identity: persisted (encrypted) locally per owner and synced with
 * relays as NIP-44-encrypted kind-30078 events.
 *
 * [openFor] loads the local cache and starts relay sync; edits write through to disk *and* publish
 * to relays; events arriving from relays merge back in without re-publishing.
 */
class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ContactStore(app)
    private val relayStore = RelayStore(app)
    private val ccHelper = ContactsContractHelper(app)
    private var owner: String? = null
    private var identity: NostrIdentity? = null
    private var sync: SyncManager? = null

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /** User-managed relay set for the contact data store (URL + enabled + durability hint). */
    private val _relays = MutableStateFlow<List<RelayConfig>>(emptyList())
    val relays: StateFlow<List<RelayConfig>> = _relays.asStateFlow()

    suspend fun openFor(identity: NostrIdentity) {
        val ownerPubkey = identity.pubKeyHex
        if (owner == ownerPubkey) return
        owner = ownerPubkey
        this.identity = identity

        _contacts.value = withContext(Dispatchers.IO) {
            if (store.exists(ownerPubkey)) {
                store.load(ownerPubkey).sortedBy { it.displayName.lowercase() }
            } else {
                // First run for this identity: seed sample data (temporary, local-only — not published).
                SampleContacts.generate()
                    .sortedBy { it.displayName.lowercase() }
                    .also { store.save(ownerPubkey, it) }
            }
        }

        // Load (or seed) the relay set, then start sync against the enabled relays.
        _relays.value = withContext(Dispatchers.IO) {
            if (relayStore.exists()) relayStore.load()
            else DEFAULT_RELAY_CONFIGS.also { relayStore.save(it) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            ccHelper.fullSync(account(ownerPubkey), _contacts.value)
        }

        startSync(identity)
        detectLocalRelay()
    }

    /** (Re)start relay sync against the currently-enabled relays. */
    private fun startSync(identity: NostrIdentity) {
        sync?.stop()
        val manager = SyncManager(
            relays = enabledRelayUrls(),
            scope = viewModelScope,
            onRemoteContact = { remote -> mergeRemote(remote) },
            onRemoteDelete = { id -> removeLocal(id) },
        )
        sync = manager
        viewModelScope.launch { manager.state.collect { _syncState.value = it } }
        manager.start(identity)
    }

    private fun enabledRelayUrls(): List<String> = _relays.value.filter { it.enabled }.map { it.url }

    fun closeSession() {
        sync?.stop()
        sync = null
        owner = null
        identity = null
        _contacts.value = emptyList()
        _relays.value = emptyList()
        _syncState.value = SyncState()
    }

    // ── Relay management ─────────────────────────────────────────────────────

    /** Add a relay (no-op if the URL already exists), persist, and re-sync so it receives copies. */
    fun addRelay(url: String, durability: Durability = Durability.FREE) {
        val clean = url.trim()
        if (clean.isBlank() || _relays.value.any { it.url == clean }) return
        updateRelays(_relays.value + RelayConfig(clean, enabled = true, durability), reseed = true)
    }

    fun removeRelay(url: String) {
        updateRelays(_relays.value.filterNot { it.url == url }, reseed = false)
    }

    fun setRelayEnabled(url: String, enabled: Boolean) {
        updateRelays(
            _relays.value.map { if (it.url == url) it.copy(enabled = enabled) else it },
            reseed = enabled, // newly enabled relay needs the current contacts pushed to it
        )
    }

    fun setRelayDurability(url: String, durability: Durability) {
        // Durability is a hint only — no need to restart sync or republish.
        updateRelays(_relays.value.map { if (it.url == url) it.copy(durability = durability) else it }, reseed = false)
    }

    /** Probe for an on-device relay (e.g. Citrine) and add it as a LOCAL relay if found. */
    fun detectLocalRelay() {
        if (_relays.value.any { it.url == LOCAL_RELAY_URL }) return
        viewModelScope.launch {
            if (LocalRelayProbe.isPresent()) {
                addRelay(LOCAL_RELAY_URL, Durability.LOCAL)
            }
        }
    }

    private fun updateRelays(next: List<RelayConfig>, reseed: Boolean) {
        _relays.value = next
        viewModelScope.launch(Dispatchers.IO) { relayStore.save(next) }
        identity?.let {
            startSync(it)
            // Push every contact so a newly added/enabled relay ends up with a full copy.
            if (reseed) viewModelScope.launch { _contacts.value.forEach { c -> sync?.publishContact(c) } }
        }
    }

    /**
     * Erase this device's local footprint for the active owner: the encrypted on-disk cache and
     * every mirrored row in the system contacts store. Does NOT touch relay-stored events — those
     * cannot be guaranteed deleted. Call before signing out.
     */
    fun wipeLocalData() {
        val ownerPubkey = owner ?: return
        sync?.stop()
        sync = null
        viewModelScope.launch(Dispatchers.IO) {
            ccHelper.fullSync(account(ownerPubkey), emptyList())
            store.delete(ownerPubkey)
        }
        owner = null
        _contacts.value = emptyList()
        _syncState.value = SyncState()
    }

    fun get(id: String): Contact? = _contacts.value.firstOrNull { it.id == id }

    fun upsert(contact: Contact) {
        _contacts.update { merge(it, contact) }
        persist()
        viewModelScope.launch { sync?.publishContact(contact) }
        owner?.let { viewModelScope.launch(Dispatchers.IO) { ccHelper.upsert(account(it), contact) } }
    }

    fun delete(id: String) {
        _contacts.update { list -> list.filterNot { it.id == id } }
        persist()
        viewModelScope.launch { sync?.publishDeletion(id) }
        owner?.let { viewModelScope.launch(Dispatchers.IO) { ccHelper.delete(account(it), id) } }
    }

    /**
     * Toggle favorite for [ids]. If every selected contact is already a favorite they're un-favorited;
     * otherwise they're favorited — unless that would exceed [MAX_FAVORITES], in which case nothing
     * changes and [FavoriteOutcome.EXCEEDS_LIMIT] is returned. Changes sync to disk and relays.
     */
    fun toggleFavorite(ids: Set<String>): FavoriteOutcome {
        if (ids.isEmpty()) return FavoriteOutcome.UNFAVORITED
        val list = _contacts.value
        val selected = list.filter { it.id in ids }
        val makeFavorite = !selected.all { it.favorite }

        if (makeFavorite) {
            val favoritesOutsideSelection = list.count { it.favorite && it.id !in ids }
            if (favoritesOutsideSelection + selected.size > MAX_FAVORITES) {
                return FavoriteOutcome.EXCEEDS_LIMIT
            }
        }

        val changed = selected.map { it.copy(favorite = makeFavorite) }
        _contacts.update { current ->
            val byId = changed.associateBy { it.id }
            current.map { byId[it.id] ?: it }.sortedBy { it.displayName.lowercase() }
        }
        persist()
        viewModelScope.launch { changed.forEach { sync?.publishContact(it) } }
        owner?.let { o -> viewModelScope.launch(Dispatchers.IO) { changed.forEach { ccHelper.upsert(account(o), it) } } }
        return if (makeFavorite) FavoriteOutcome.FAVORITED else FavoriteOutcome.UNFAVORITED
    }

    /** Delete many contacts at once: one in-memory update + disk write, then per-id relay/phone removal. */
    fun deleteMany(ids: Set<String>) {
        if (ids.isEmpty()) return
        _contacts.update { list -> list.filterNot { it.id in ids } }
        persist()
        viewModelScope.launch { ids.forEach { sync?.publishDeletion(it) } }
        owner?.let { o ->
            viewModelScope.launch(Dispatchers.IO) { ids.forEach { ccHelper.delete(account(o), it) } }
        }
    }

    /** Apply a contact received from a relay — update local + disk, but do NOT re-publish. */
    private fun mergeRemote(remote: Contact) {
        _contacts.update { merge(it, remote) }
        persist()
        owner?.let { viewModelScope.launch(Dispatchers.IO) { ccHelper.upsert(account(it), remote) } }
    }

    private fun removeLocal(id: String) {
        _contacts.update { list -> list.filterNot { it.id == id } }
        persist()
        owner?.let { viewModelScope.launch(Dispatchers.IO) { ccHelper.delete(account(it), id) } }
    }

    private fun merge(list: List<Contact>, contact: Contact): List<Contact> {
        val idx = list.indexOfFirst { it.id == contact.id }
        val merged = if (idx >= 0) list.toMutableList().also { it[idx] = contact } else list + contact
        return merged.sortedBy { it.displayName.lowercase() }
    }

    private fun persist() {
        val ownerPubkey = owner ?: return
        val snapshot = _contacts.value
        viewModelScope.launch(Dispatchers.IO) { store.save(ownerPubkey, snapshot) }
    }

    private fun account(pubKeyHex: String) =
        Account(pubKeyHex, ContactsContractHelper.ACCOUNT_TYPE)

    companion object {
        /** Max contacts that can be pinned to the Favorites section. */
        const val MAX_FAVORITES = 7
    }
}

/** Result of [ContactsViewModel.toggleFavorite]. */
enum class FavoriteOutcome { FAVORITED, UNFAVORITED, EXCEEDS_LIMIT }
