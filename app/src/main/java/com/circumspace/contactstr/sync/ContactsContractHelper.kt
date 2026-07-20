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
        val rawId = findRawContactId(account, contactId) ?: return@guard
        resolver.delete(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
            null, null,
        )
    }

    /**
     * Reconcile the full [contacts] list against ContactsContract for this account.
     * Contacts removed from [contacts] are deleted; new/changed ones are upserted.
     */
    fun fullSync(account: Account, contacts: List<Contact>) = guard {
        val existingIds = fetchExistingSourceIds(account)
        val incomingIds = contacts.map { it.id }.toSet()

        // Delete stale rows
        (existingIds - incomingIds).forEach { delete(account, it) }

        // Upsert current contacts
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

    private fun findRawContactId(account: Account, sourceId: String): Long? =
        findRawContact(account, sourceId)?.first

    /** Returns (rawContactId, last-written fingerprint from SYNC1) for [sourceId], or null. */
    private fun findRawContact(account: Account, sourceId: String): Pair<Long, String?>? {
        val cursor: Cursor = resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.SYNC1),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=? AND ${RawContacts.SOURCE_ID}=?",
            arrayOf(ACCOUNT_TYPE, account.name, sourceId),
            null,
        ) ?: return null
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null
        }
    }

    /** Stable digest of every field mirrored into ContactsContract; stored in SYNC1 on write. */
    private fun fingerprint(c: Contact): String =
        listOf(c.displayName, c.phone, c.email, c.address, c.website, c.note, c.nostr, c.birthday.orEmpty())
            .joinToString("\u0001")
            .hashCode()
            .toString()

    private fun fetchExistingSourceIds(account: Account): Set<String> {
        val cursor: Cursor = resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.SOURCE_ID),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=? AND ${RawContacts.DELETED}=0",
            arrayOf(ACCOUNT_TYPE, account.name),
            null,
        ) ?: return emptySet()
        return cursor.use {
            buildSet {
                while (it.moveToNext()) {
                    it.getString(0)?.let { id -> add(id) }
                }
            }
        }
    }

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

    companion object {
        const val ACCOUNT_TYPE = "com.circumspace.contactstr"
    }
}
