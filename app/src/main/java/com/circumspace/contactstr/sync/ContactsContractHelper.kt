package com.circumspace.contactstr.sync

import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.*
import android.provider.ContactsContract.RawContacts
import com.circumspace.contactstr.domain.Contact

/**
 * Writes [Contact] objects to Android's ContactsContract so they appear in the Phone, Mail,
 * and Messaging apps. Each contact is stored as a RawContact under the Contactstr account type
 * and tagged with the contact's UUID in the SOURCE_ID column for idempotent upserts.
 */
class ContactsContractHelper(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    fun upsert(account: Account, contact: Contact) = guard {
        val fp = fingerprint(contact)
        val existing = findRawContact(account, contact.id)
        when {
            existing == null -> insert(account, contact, fp)
            // Fingerprint matches what we last wrote — nothing to do. This makes the startup
            // fullSync near-free instead of delete-and-reinserting every contact's rows.
            existing.second == fp -> Unit
            else -> update(existing.first, contact, fp)
        }
    }

    fun delete(account: Account, contactId: String) = guard {
        deleteRawContactsBySource(account, contactId)
    }

    /**
     * Messengers this contact is *actually reachable on*, detected via the data rows the messenger
     * apps write onto the aggregated system contact — they create these only for numbers registered
     * on their service. This is how the stock Contacts app decides whether to offer "Message on
     * WhatsApp/Signal/Telegram", and it's far more reliable than "is the app installed". Requires
     * the contact to be mirrored to system contacts (we do) and the messenger to have synced it.
     * Returns the data-row id so the caller launches the messenger's own chat via ACTION_VIEW.
     */
    fun messengerLinks(account: Account, contactId: String): List<MessengerLink> =
        runCatching {
            val aggregateId = aggregatedContactId(account, contactId) ?: return@runCatching emptyList()
            val byMime = Messenger.entries.associateBy { it.mimeType }
            val cursor = resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE),
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE} IN (?,?,?)",
                arrayOf(
                    aggregateId.toString(),
                    Messenger.WHATSAPP.mimeType,
                    Messenger.TELEGRAM.mimeType,
                    Messenger.SIGNAL.mimeType,
                ),
                null,
            ) ?: return@runCatching emptyList()
            // One link per messenger (first data row wins), stable order.
            val out = LinkedHashMap<Messenger, MessengerLink>()
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val m = byMime[it.getString(1)] ?: continue
                    if (m !in out) out[m] = MessengerLink(m, id, m.mimeType)
                }
            }
            out.values.toList()
        }.getOrDefault(emptyList())

    /** The aggregated Contact id our raw contact belongs to (messenger rows hang off the aggregate). */
    private fun aggregatedContactId(account: Account, sourceId: String): Long? {
        val cursor = resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.CONTACT_ID),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=? AND " +
                "${RawContacts.SOURCE_ID}=? AND ${RawContacts.DELETED}=0",
            arrayOf(ACCOUNT_TYPE, account.name, sourceId),
            null,
        ) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    /**
     * Physically remove EVERY raw-contact row for [sourceId] under this account. Uses the
     * sync-adapter URI so the provider actually deletes the rows instead of soft-marking them
     * `DELETED=1` (a plain caller's delete on a sync-account row is retained for the adapter to
     * upload — ours is a stub, so those would pile up forever). Deletes all matches, so it also
     * clears any duplicate rows a past idempotency bug created.
     */
    private fun deleteRawContactsBySource(account: Account, sourceId: String) {
        resolver.delete(
            RawContacts.CONTENT_URI.asSyncAdapter(account),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=? AND ${RawContacts.SOURCE_ID}=?",
            arrayOf(ACCOUNT_TYPE, account.name, sourceId),
        )
    }

    /**
     * Reconcile the full [contacts] list against ContactsContract for this account. Beyond deleting
     * rows for contacts no longer present, this physically purges orphans, duplicates, and
     * soft-deleted leftovers so the system mirror can't bloat (and self-heals a device that already
     * accumulated them). Clean, unchanged rows are left untouched via the fingerprint fast-path.
     */
    fun fullSync(account: Account, contacts: List<Contact>) = guard {
        val incoming = contacts.associateBy { it.id }
        fetchRawStats(account).forEach { (sourceId, stat) ->
            if (sourceId !in incoming || stat.count > 1 || stat.anyDeleted) {
                deleteRawContactsBySource(account, sourceId)
            }
        }
        contacts.forEach { upsert(account, it) }
    }

    /**
     * Runs [block], swallowing failures (e.g. [SecurityException] when the user has not granted
     * the contacts permission). System-contacts mirroring is best-effort; the encrypted local
     * store and relay sync remain the source of truth.
     */
    private inline fun guard(block: () -> Unit) {
        runCatching { block() }
    }

    fun requestSync(account: Account) {
        ContentResolver.requestSync(account, ContactsContract.AUTHORITY, Bundle())
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /** Returns (rawContactId, last-written fingerprint from SYNC1) for a *live* [sourceId], or null. */
    private fun findRawContact(account: Account, sourceId: String): Pair<Long, String?>? {
        val cursor: Cursor = resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.SYNC1),
            // DELETED=0 only: a soft-deleted leftover must never be treated as the live row, or
            // upsert would either skip (contact stays hidden) or fork a duplicate.
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=? AND " +
                "${RawContacts.SOURCE_ID}=? AND ${RawContacts.DELETED}=0",
            arrayOf(ACCOUNT_TYPE, account.name, sourceId),
            null,
        ) ?: return null
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null
        }
    }

    private data class RawStat(val count: Int, val anyDeleted: Boolean)

    /** sourceId → (row count, whether any row is soft-deleted) for this account, across all states. */
    private fun fetchRawStats(account: Account): Map<String, RawStat> {
        val cursor: Cursor = resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.SOURCE_ID, RawContacts.DELETED),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=?",
            arrayOf(ACCOUNT_TYPE, account.name),
            null,
        ) ?: return emptyMap()
        return cursor.use {
            val map = HashMap<String, RawStat>()
            while (it.moveToNext()) {
                val sourceId = it.getString(0) ?: continue
                val deleted = it.getInt(1) == 1
                val prev = map[sourceId]
                map[sourceId] = RawStat((prev?.count ?: 0) + 1, (prev?.anyDeleted ?: false) || deleted)
            }
            map
        }
    }

    /** Stable digest of every field mirrored into ContactsContract; stored in SYNC1 on write. */
    private fun fingerprint(c: Contact): String =
        listOf(c.displayName, c.phone, c.email, c.address, c.website, c.note, c.nostr, c.birthday.orEmpty())
            .joinToString("\u0001")
            .hashCode()
            .toString()

    private fun insert(account: Account, contact: Contact, fp: String) {
        val ops = buildOps(account, contact, fp)
        resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
    }

    private fun update(rawContactId: Long, contact: Contact, fp: String) {
        // Delete existing data rows and re-insert; simpler than diffing individual rows.
        resolver.delete(
            ContactsContract.Data.CONTENT_URI,
            "${ContactsContract.Data.RAW_CONTACT_ID}=?",
            arrayOf(rawContactId.toString()),
        )
        val ops = ArrayList(buildDataOps(rawContactId, contact))
        ops += ContentProviderOperation.newUpdate(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
        ).withValue(RawContacts.SYNC1, fp).build()
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun buildOps(account: Account, contact: Contact, fp: String): List<ContentProviderOperation> {
        val ops = mutableListOf<ContentProviderOperation>()

        // RawContact row
        ops += ContentProviderOperation.newInsert(
            RawContacts.CONTENT_URI.asSyncAdapter(account),
        ).apply {
            withValue(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
            withValue(RawContacts.ACCOUNT_NAME, account.name)
            withValue(RawContacts.SOURCE_ID, contact.id)
            withValue(RawContacts.SYNC1, fp)
        }.build()

        // Data rows referencing the just-inserted raw contact (back-reference index 0)
        ops += buildDataOpsWithBackRef(contact)
        return ops
    }

    private fun buildDataOps(rawContactId: Long, contact: Contact): List<ContentProviderOperation> {
        return buildDataOpsWithRef({ withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId) }, contact)
    }

    private fun buildDataOpsWithBackRef(contact: Contact): List<ContentProviderOperation> {
        return buildDataOpsWithRef({ withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0) }, contact)
    }

    private fun buildDataOpsWithRef(
        addRef: ContentProviderOperation.Builder.() -> Unit,
        contact: Contact,
    ): List<ContentProviderOperation> {
        val ops = mutableListOf<ContentProviderOperation>()
        val uri = ContactsContract.Data.CONTENT_URI

        fun op(block: ContentProviderOperation.Builder.() -> Unit): ContentProviderOperation =
            ContentProviderOperation.newInsert(uri).apply { addRef(); block() }.build()

        ops += op {
            withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            withValue(StructuredName.DISPLAY_NAME, contact.displayName)
        }

        if (contact.phone.isNotBlank()) {
            ops += op {
                withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                withValue(Phone.NUMBER, contact.phone)
                withValue(Phone.TYPE, Phone.TYPE_MOBILE)
            }
        }

        if (contact.email.isNotBlank()) {
            ops += op {
                withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                withValue(Email.ADDRESS, contact.email)
                withValue(Email.TYPE, Email.TYPE_HOME)
            }
        }

        if (contact.address.isNotBlank()) {
            ops += op {
                withValue(ContactsContract.Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                withValue(StructuredPostal.FORMATTED_ADDRESS, contact.address)
                withValue(StructuredPostal.TYPE, StructuredPostal.TYPE_HOME)
            }
        }

        if (contact.website.isNotBlank()) {
            ops += op {
                withValue(ContactsContract.Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
                withValue(Website.URL, contact.website)
                withValue(Website.TYPE, Website.TYPE_HOMEPAGE)
            }
        }

        if (contact.note.isNotBlank()) {
            ops += op {
                withValue(ContactsContract.Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                withValue(Note.NOTE, contact.note)
            }
        }

        if (contact.nostr.isNotBlank()) {
            ops += op {
                withValue(ContactsContract.Data.MIMETYPE, Im.CONTENT_ITEM_TYPE)
                withValue(Im.DATA, contact.nostr)
                withValue(Im.TYPE, Im.TYPE_CUSTOM)
                withValue(Im.LABEL, "Nostr")
                withValue(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
                withValue(Im.CUSTOM_PROTOCOL, "nostr")
            }
        }

        if (!contact.birthday.isNullOrBlank()) {
            // START_DATE accepts both "YYYY-MM-DD" and year-less "--MM-DD". Google/Samsung Calendar
            // surface TYPE_BIRTHDAY events automatically; our own calendar (Path B) covers the rest.
            ops += op {
                withValue(ContactsContract.Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
                withValue(Event.START_DATE, contact.birthday)
                withValue(Event.TYPE, Event.TYPE_BIRTHDAY)
            }
        }

        return ops
    }

    private fun android.net.Uri.asSyncAdapter(account: Account): android.net.Uri =
        buildUpon()
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()

    /** A messenger a contact is reachable on, with the exact mimetype rows carry in ContactsContract. */
    enum class Messenger(val label: String, val mimeType: String) {
        WHATSAPP("WhatsApp", "vnd.android.cursor.item/vnd.com.whatsapp.profile"),
        TELEGRAM("Telegram", "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile"),
        SIGNAL("Signal", "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"),
    }

    /** A launchable "message on <app>" action: [dataId] is the ContactsContract Data row to VIEW. */
    data class MessengerLink(val messenger: Messenger, val dataId: Long, val mimeType: String)

    companion object {
        const val ACCOUNT_TYPE = "com.circumspace.contactstr"
    }
}
