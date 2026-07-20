package com.circumspace.contactstr.data

import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.domain.ContactPhoto
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contact ↔ JSON. Used both for the local encrypted cache and as the (pre-encryption) payload of
 * kind-30078 relay events, so a contact round-trips byte-for-byte between disk and relay.
 */
object ContactJson {
    fun toJsonObject(c: Contact): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("displayName", c.displayName)
        put("phone", c.phone)
        put("email", c.email)
        put("address", c.address)
        put("website", c.website)
        put("nostr", c.nostr)
        put("note", c.note)
        put("photoUri", c.photoUri ?: JSONObject.NULL)
        put("photo", c.photo?.let { photoToJson(it) } ?: JSONObject.NULL)
        put("favorite", c.favorite)
        put("updatedAt", c.updatedAt)
        put("categories", org.json.JSONArray().apply { c.categories.forEach { put(it) } })
        put("birthday", c.birthday ?: JSONObject.NULL)
    }

    fun toJsonString(c: Contact): String = toJsonObject(c).toString()

    fun fromJsonObject(o: JSONObject): Contact = Contact(
        id = o.getString("id"),
        displayName = o.getString("displayName"),
        phone = o.optString("phone"),
        email = o.optString("email"),
        address = o.optString("address"),
        website = o.optString("website"),
        nostr = o.optString("nostr"),
        note = o.optString("note"),
        photoUri = if (o.isNull("photoUri")) null else o.optString("photoUri").ifEmpty { null },
        photo = o.optJSONObject("photo")?.let { photoFromJson(it) },
        favorite = o.optBoolean("favorite", false),
        updatedAt = o.optLong("updatedAt", 0L),
        categories = o.optJSONArray("categories")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList(),
        birthday = if (o.isNull("birthday")) null else o.optString("birthday").ifBlank { null },
    )

    fun fromJsonString(s: String): Contact? = runCatching { fromJsonObject(JSONObject(s)) }.getOrNull()

    private fun photoToJson(p: ContactPhoto): JSONObject = JSONObject().apply {
        put("urls", JSONArray().apply { p.urls.forEach { put(it) } })
        put("sha256", p.sha256)
        put("key", p.key)
        put("nonce", p.nonce)
        put("mime", p.mime)
    }

    /** Tolerant of a malformed/partial photo blob (returns null) so one bad field can't drop a contact. */
    private fun photoFromJson(o: JSONObject): ContactPhoto? {
        val urls = o.optJSONArray("urls")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }.orEmpty()
        val sha256 = o.optString("sha256")
        val key = o.optString("key")
        val nonce = o.optString("nonce")
        if (urls.isEmpty() || sha256.isBlank() || key.isBlank() || nonce.isBlank()) return null
        return ContactPhoto(
            urls = urls,
            sha256 = sha256,
            key = key,
            nonce = nonce,
            mime = o.optString("mime").ifBlank { "image/jpeg" },
        )
    }
}
