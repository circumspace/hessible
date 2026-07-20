package com.circumspace.contactstr.data.persistence

import android.content.Context
import com.circumspace.contactstr.data.ContactJson
import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.security.SecureBlob
import org.json.JSONArray
import java.io.File

/**
 * Encrypted-at-rest local cache of contacts, scoped per owner (npub-hex). Serialized to JSON and
 * sealed with [SecureBlob]. Once relay sync is active this is the decrypted cache kept in step
 * with kind-30078 events.
 */
class ContactStore(private val context: Context) {
    private fun file(owner: String) = File(context.filesDir, "contacts_${owner.take(16)}.bin")

    fun exists(owner: String): Boolean = file(owner).exists()

    /** Permanently delete this owner's encrypted local cache file. */
    fun delete(owner: String) {
        file(owner).delete()
    }

    fun load(owner: String): List<Contact> {
        val f = file(owner)
        if (!f.exists()) return emptyList()
        return runCatching {
            val json = String(SecureBlob.open(f.readBytes()), Charsets.UTF_8)
            val array = JSONArray(json)
            (0 until array.length()).map { ContactJson.fromJsonObject(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(owner: String, contacts: List<Contact>) {
        val array = JSONArray()
        contacts.forEach { array.put(ContactJson.toJsonObject(it)) }
        file(owner).writeBytes(SecureBlob.seal(array.toString().toByteArray(Charsets.UTF_8)))
    }
}
