package com.circumspace.contactstr.data.persistence

import android.content.Context
import com.circumspace.contactstr.data.ContactJson
import com.circumspace.contactstr.domain.Contact
import com.circumspace.contactstr.security.SecureBlob
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A pending relay publish, stored as an *intent* (not a signed frame) so it survives a failed or
 *  unavailable signer (Amber) and can be re-signed + retried later. */
data class OutboxOp(
    val contactId: String,
    val type: Type,
    /** Stable event timestamp (epoch seconds) — fixed at enqueue so retries don't bump it. */
    val createdAt: Long,
    /** The contact to publish, for [Type.UPSERT]. Null for a deletion. */
    val contact: Contact?,
) {
    enum class Type { UPSERT, DELETE }
}

/**
 * Encrypted-at-rest outbox of relay publishes awaiting an OK ack. Because entries hold plaintext
 * *intents* rather than pre-signed events, an operation issued while the signer (Amber) is
 * unavailable is not lost: it's re-signed and re-sent on the next drain. Keyed by contact id —
 * the latest op per contact wins (a delete supersedes a pending upsert, and vice-versa).
 */
class OutboxStore(private val context: Context) {
    private val file get() = File(context.filesDir, "outbox.bin")

    fun load(): List<OutboxOp> {
        val f = file
        if (!f.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(String(SecureBlob.open(f.readBytes()), Charsets.UTF_8))
            (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(ops: Collection<OutboxOp>) {
        if (ops.isEmpty()) {
            file.delete()
            return
        }
        val array = JSONArray()
        ops.forEach { array.put(toJson(it)) }
        file.writeBytes(SecureBlob.seal(array.toString().toByteArray(Charsets.UTF_8)))
    }

    fun clear() {
        file.delete()
    }

    private fun toJson(op: OutboxOp): JSONObject = JSONObject().apply {
        put("id", op.contactId)
        put("type", op.type.name)
        put("createdAt", op.createdAt)
        put("contact", op.contact?.let { ContactJson.toJsonObject(it) } ?: JSONObject.NULL)
    }

    private fun fromJson(o: JSONObject): OutboxOp? {
        val type = runCatching { OutboxOp.Type.valueOf(o.getString("type")) }.getOrNull() ?: return null
        return OutboxOp(
            contactId = o.getString("id"),
            type = type,
            createdAt = o.optLong("createdAt"),
            contact = if (o.isNull("contact")) null else ContactJson.fromJsonObject(o.getJSONObject("contact")),
        )
    }
}
