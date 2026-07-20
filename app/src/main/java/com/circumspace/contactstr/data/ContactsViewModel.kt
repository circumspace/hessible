package com.circumspace.contactstr.data

import android.accounts.Account
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.circumspace.contactstr.crypto.ImageCrypto
import com.circumspace.contactstr.crypto.NostrIdentity
import com.circumspace.contactstr.data.blob.BlossomBlobStore
import com.circumspace.contactstr.data.blob.ImageProcessing
import com.circumspace.contactstr.data.nostr.LocalRelayProbe
import com.circumspace.contactstr.data.nostr.SyncManager
import com.circumspace.contactstr.data.nostr.SyncState
import com.circumspace.contactstr.data.persistence.BlossomServerStore
import com.circumspace.contactstr.data.persistence.ContactStore
import com.circumspace.contactstr.data.persistence.OutboxStore
import com.circumspace.contactstr.data.persistence.RelayStore
import com.circumspace.contactstr.data.persistence.TombstoneStore
import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.domain.ContactPhoto
import com.circumspace.contactstr.sync.BirthdayCalendarHelper
import com.circumspace.contactstr.sync.ContactsContractHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val outboxStore = OutboxStore(app)
    private val tombstoneStore = TombstoneStore(app)
    private val blossomServerStore = BlossomServerStore(app)
    private val blobStore = BlossomBlobStore()
    private val ccHelper = ContactsContractHelper(app)
    private val birthdayCalendar = BirthdayCalendarHelper(app)
    private var owner: String? = null
    private var identity: NostrIdentity? = null
    private var sync: SyncManager? = null
    private var persistJob: Job? = null

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /** User-managed relay set for the contact data store (URL + enabled + durability hint). */
    private val _relays = MutableStateFlow<List<RelayConfig>>(emptyList())
    val relays: StateFlow<List<RelayConfig>> = _relays.asStateFlow()

    /** Blossom servers that encrypted contact photos are uploaded to (mirrored across all of them). */
    private val _blossomServers = MutableStateFlow<List<String>>(emptyList())
    val blossomServers: StateFlow<List<String>> = _blossomServers.asStateFlow()

    suspend fun openFor(identity: NostrIdentity) {
        val ownerPubkey = identity.pubKeyHex
        if (owner == ownerPubkey) return
        owner = ownerPubkey
        this.identity = identity

        // Start empty on first run and let relay sync populate — seeding sample data here was
        // actively harmful once multi-device sync existed (a reseed could push samples to relays).
        // Purge any locally-cached contact that we've since tombstoned: a past bug let a relay's
        // stale copy resurrect a deleted contact into the local cache, and durable tombstones now
        // let us sweep those ghosts out on load (and keep them from re-arriving via sync).
        _contacts.value = withContext(Dispatchers.IO) {
            val loaded = store.load(ownerPubkey)
            val tombstoned = tombstoneStore.load(ownerPubkey).keys
            val alive = loaded.filterNot { it.id in tombstoned }
            if (alive.size != loaded.size) {
                store.save(ownerPubkey, alive)
                val account = account(ownerPubkey)
                loaded.filter { it.id in tombstoned }.forEach { ghost ->
                    ccHelper.delete(account, ghost.id)
                    birthdayCalendar.deleteBirthday(ghost.id)
                }
            }
            alive.sortedBy { it.displayName.lowercase() }
        }

        // Load (or seed) the relay set, then start sync against the enabled relays.
        _relays.value = withContext(Dispatchers.IO) {
            if (relayStore.exists()) relayStore.load()
            else DEFAULT_RELAY_CONFIGS.also { relayStore.save(it) }
        }

        // Load (or seed) the Blossom server set for encrypted photo uploads.
        _blossomServers.value = withContext(Dispatchers.IO) {
            if (blossomServerStore.exists()) blossomServerStore.load()
            else BlossomServerStore.DEFAULTS.also { blossomServerStore.save(it) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            ccHelper.fullSync(account(ownerPubkey), _contacts.value)
            birthdayCalendar.sync(_contacts.value)
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
            outboxStore = outboxStore,
            tombstoneStore = tombstoneStore,
            onRemoteContact = { remote -> mergeRemote(remote) },
            onRemoteDelete = { id -> removeLocal(id) },
        )
        sync = manager
        viewModelScope.launch { manager.state.collect { _syncState.value = it } }
        manager.start(identity)
    }

    private fun enabledRelayUrls(): List<String> = _relays.value.filter { it.enabled }.map { it.url }

    fun closeSession() {
        persistJob?.cancel()
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

    // ── Contact photos (encrypted Blossom blobs) ────────────────────────────────

    /**
     * Process → encrypt → upload a picked image, returning a [ContactPhoto] descriptor to store on
     * the contact (which then syncs, encrypted, inside the 30078 event). Returns null if the image
     * can't be read, no servers are configured, or every server rejected the upload (e.g. offline).
     * Runs entirely off the main thread.
     */
    suspend fun uploadPhoto(uri: Uri): ContactPhoto? {
        val id = identity ?: return null
        val servers = _blossomServers.value
        if (servers.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            val plain = ImageProcessing.process(getApplication(), uri) ?: return@withContext null
            val enc = ImageCrypto.encrypt(plain)
            val sha = ImageCrypto.sha256Hex(enc.ciphertext)
            val urls = blobStore.upload(enc.ciphertext, sha, servers, id)
            if (urls.isEmpty()) return@withContext null
            ContactPhoto(urls = urls, sha256 = sha, key = enc.keyHex, nonce = enc.nonceHex, mime = ImageProcessing.MIME)
        }
    }

    fun addBlossomServer(url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isBlank() || _blossomServers.value.any { it.equals(clean, ignoreCase = true) }) return
        updateBlossomServers(_blossomServers.value + clean)
    }

    fun removeBlossomServer(url: String) {
        updateBlossomServers(_blossomServers.value.filterNot { it == url })
    }

    private fun updateBlossomServers(next: List<String>) {
        _blossomServers.value = next
        viewModelScope.launch(Dispatchers.IO) { blossomServerStore.save(next) }
    }

    /**
     * Erase this device's local footprint for the active owner: the encrypted on-disk cache and
     * every mirrored row in the system contacts store. Does NOT touch relay-stored events — those
     * cannot be guaranteed deleted. Call before signing out.
     */
    fun wipeLocalData() {
        val ownerPubkey = owner ?: return
        persistJob?.cancel() // don't let a debounced save recreate the file after we delete it
        sync?.stop()
        sync = null
        viewModelScope.launch(Dispatchers.IO) {
            ccHelper.fullSync(account(ownerPubkey), emptyList())
            birthdayCalendar.sync(emptyList())
            store.delete(ownerPubkey)
            outboxStore.clear()
        }
        owner = null
        _contacts.value = emptyList()
        _syncState.value = SyncState()
    }

    /**
     * On app-foreground: re-establish sync — revive dropped relay sockets, re-pull remote changes,
     * and flush the outbox (where an external signer like Amber can finally service the request).
     */
    fun retrySync() {
        sync?.resync()
    }

    fun get(id: String): Contact? = _contacts.value.firstOrNull { it.id == id }

    /** Messengers this contact is actually reachable on (via system-contact data rows); off-main. */
    suspend fun messengerLinks(contactId: String): List<ContactsContractHelper.MessengerLink> {
        val ownerPubkey = owner ?: return emptyList()
        return withContext(Dispatchers.IO) { ccHelper.messengerLinks(account(ownerPubkey), contactId) }
    }

    fun upsert(contact: Contact) {
        // Stamp the edit time so this version wins conflict resolution over any older relay copy.
        val stamped = contact.copy(updatedAt = nowSec())
        _contacts.update { merge(it, stamped) }
        persist()
        viewModelScope.launch { sync?.publishContact(stamped) }
        owner?.let { viewModelScope.launch(Dispatchers.IO) { ccHelper.upsert(account(it), stamped) } }
        viewModelScope.launch(Dispatchers.IO) { birthdayCalendar.upsertBirthday(stamped) }
    }

    fun delete(id: String) {
        _contacts.update { list -> list.filterNot { it.id == id } }
        persist()
        viewModelScope.launch { sync?.publishDeletion(id) }
        owner?.let { viewModelScope.launch(Dispatchers.IO) { ccHelper.delete(account(it), id) } }
        viewModelScope.launch(Dispatchers.IO) { birthdayCalendar.deleteBirthday(id) }
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

        val now = nowSec()
        val changed = selected.map { it.copy(favorite = makeFavorite, updatedAt = now) }
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
        viewModelScope.launch(Dispatchers.IO) { ids.forEach { birthdayCalendar.deleteBirthday(it) } }
    }

    /** Apply a contact received from a relay — update local + disk, but do NOT re-publish. */
    private fun mergeRemote(remote: Contact) {
        // Conflict resolution + resync de-churn in one guard: apply a remote copy only when it's
        // strictly newer than what we hold. This stops the "two versions flip-flop" bug (a stale
        // relay copy can't overwrite a newer edit) AND skips re-persist/re-mirror I/O when the full
        // history replays on every foreground resync. Legacy records (updatedAt 0) tie → keep local.
        val current = _contacts.value.firstOrNull { it.id == remote.id }
        if (current != null && remote.updatedAt <= current.updatedAt) return
        _contacts.update { merge(it, remote) }
        persist()
        owner?.let { viewModelScope.launch(Dispatchers.IO) { ccHelper.upsert(account(it), remote) } }
        viewModelScope.launch(Dispatchers.IO) { birthdayCalendar.upsertBirthday(remote) }
    }

    private fun removeLocal(id: String) {
        // Deletions replay on every resync; if it's already gone, launch no I/O and touch no state.
        if (_contacts.value.none { it.id == id }) return
        _contacts.update { list -> list.filterNot { it.id == id } }
        persist()
        owner?.let { viewModelScope.launch(Dispatchers.IO) { ccHelper.delete(account(it), id) } }
        viewModelScope.launch(Dispatchers.IO) { birthdayCalendar.deleteBirthday(id) }
    }

    private fun merge(list: List<Contact>, contact: Contact): List<Contact> {
        val idx = list.indexOfFirst { it.id == contact.id }
        val merged = if (idx >= 0) list.toMutableList().also { it[idx] = contact } else list + contact
        return merged.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Debounced local save: rapid changes (notably a startup sync merging many remote contacts)
     * collapse into a single encrypt + write after the burst settles, instead of one per change.
     */
    private fun persist() {
        val ownerPubkey = owner ?: return
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(PERSIST_DEBOUNCE_MS)
            store.save(ownerPubkey, _contacts.value)
        }
    }

    private fun nowSec() = System.currentTimeMillis() / 1000

    private fun account(pubKeyHex: String) =
        Account(pubKeyHex, ContactsContractHelper.ACCOUNT_TYPE)

    companion object {
        /** Max contacts that can be pinned to the Favorites section. */
        const val MAX_FAVORITES = 7

        /** Window to coalesce rapid local saves (e.g. a startup sync burst) into one write. */
        private const val PERSIST_DEBOUNCE_MS = 400L
    }
}

/** Result of [ContactsViewModel.toggleFavorite]. */
enum class FavoriteOutcome { FAVORITED, UNFAVORITED, EXCEEDS_LIMIT }
