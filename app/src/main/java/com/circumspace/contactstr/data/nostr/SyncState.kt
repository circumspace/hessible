package com.circumspace.contactstr.data.nostr

data class RelayConn(val url: String, val connected: Boolean)

/** Live sync status surfaced in Settings. */
data class SyncState(
    val relays: List<RelayConn> = emptyList(),
    val syncing: Boolean = false,
    val lastSyncAtMs: Long? = null,
    val published: Int = 0,
    val received: Int = 0,
    val pendingWrites: Int = 0,
) {
    val connectedCount: Int get() = relays.count { it.connected }
}
