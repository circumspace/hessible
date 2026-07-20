package com.circumspace.contactstr.data

import com.circumspace.contactstr.domain.Contact
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
        put("favorite", c.favorite)
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
        favorite = o.optBoolean("favorite", false),
        categories = o.optJSONArray("categories")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList(),
        birthday = if (o.isNull("birthday")) null else o.optString("birthday").ifBlank { null },
    )

    fun fromJsonString(s: String): Contact? = runCatching { fromJsonObject(JSONObject(s)) }.getOrNull()
}
