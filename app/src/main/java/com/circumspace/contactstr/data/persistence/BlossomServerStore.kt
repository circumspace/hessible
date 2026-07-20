package com.circumspace.contactstr.data.persistence

import android.content.Context
import com.circumspace.contactstr.security.SecureBlob
import org.json.JSONArray
import java.io.File

/**
 * Encrypted-at-rest store for the user's Blossom server URLs (where encrypted contact photos are
 * uploaded). Device-global like [RelayStore] — servers are infrastructure, not per-owner data.
 */
class BlossomServerStore(private val context: Context) {
    private val file get() = File(context.filesDir, "blossom_servers.bin")

    fun exists(): Boolean = file.exists()

    fun load(): List<String> {
        val f = file
        if (!f.exists()) return emptyList()
        return runCatching {
            val json = String(SecureBlob.open(f.readBytes()), Charsets.UTF_8)
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    fun save(servers: List<String>) {
        val array = JSONArray().apply { servers.forEach { put(it) } }
        file.writeBytes(SecureBlob.seal(array.toString().toByteArray(Charsets.UTF_8)))
    }

    companion object {
        /**
         * Sane defaults. Blossom servers vary in whether they accept uploads from arbitrary keys;
         * these are commonly open, and the user can add/remove their own in Settings.
         */
        val DEFAULTS = listOf(
            "https://blossom.band",
            "https://blossom.primal.net",
        )
    }
}
