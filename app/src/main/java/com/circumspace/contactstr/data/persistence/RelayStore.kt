package com.circumspace.contactstr.data.persistence

import android.content.Context
import com.circumspace.contactstr.data.Durability
import com.circumspace.contactstr.data.RelayConfig
import com.circumspace.contactstr.security.SecureBlob
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Encrypted-at-rest store for the user's relay configuration (URL + enabled + durability hint).
 * Device-global (not per-owner): relays are an infrastructure choice, and local-relay detection
 * is device-wide. Sealed with [SecureBlob] like the contact cache.
 */
class RelayStore(private val context: Context) {
    private val file get() = File(context.filesDir, "relays.bin")

    fun exists(): Boolean = file.exists()

    fun load(): List<RelayConfig> {
        val f = file
        if (!f.exists()) return emptyList()
        return runCatching {
            val json = String(SecureBlob.open(f.readBytes()), Charsets.UTF_8)
            val array = JSONArray(json)
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(relays: List<RelayConfig>) {
        val array = JSONArray()
        relays.forEach { array.put(toJson(it)) }
        file.writeBytes(SecureBlob.seal(array.toString().toByteArray(Charsets.UTF_8)))
    }

    private fun toJson(r: RelayConfig): JSONObject = JSONObject().apply {
        put("url", r.url)
        put("enabled", r.enabled)
        put("durability", r.durability.name)
    }

    private fun fromJson(o: JSONObject): RelayConfig = RelayConfig(
        url = o.getString("url"),
        enabled = o.optBoolean("enabled", true),
        durability = runCatching { Durability.valueOf(o.optString("durability")) }
            .getOrDefault(Durability.FREE),
    )
}
