package com.circumspace.contactstr.sync

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import com.circumspace.contactstr.data.BirthdayDate
import com.circumspace.contactstr.domain.Contact

/**
 * Path B for birthdays: an app-owned **local** calendar ("Hessible Birthdays") holding one all-day,
 * yearly-recurring event per contact with a birthday. Because it's a real [CalendarContract]
 * calendar, every calendar app displays it — including on de-Googled devices where the
 * ContactsContract birthday (Path A) never reaches a calendar. Best-effort: all provider access is
 * guarded so a missing WRITE_CALENDAR permission degrades to a no-op.
 */
class BirthdayCalendarHelper(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** Reconcile the whole calendar against [contacts]: add/update/remove birthday events. */
    fun sync(contacts: List<Contact>) = guard {
        val calId = ensureCalendar() ?: return@guard
        val existing = existingEvents(calId) // contactId -> (eventId, dtStart, title)
        val wanted = contacts.filter { !it.birthday.isNullOrBlank() }

        val wantedIds = wanted.map { it.id }.toSet()
        (existing.keys - wantedIds).forEach { staleId ->
            existing[staleId]?.let { deleteEvent(it.id) }
        }
        wanted.forEach { upsertInto(calId, existing, it) }
    }

    /** Add/update the birthday event for one contact (or remove it if the birthday was cleared). */
    fun upsertBirthday(contact: Contact) = guard {
        val calId = ensureCalendar() ?: return@guard
        if (contact.birthday.isNullOrBlank()) {
            deleteBirthday(contact.id)
            return@guard
        }
        upsertInto(calId, existingEvents(calId), contact)
    }

    fun deleteBirthday(contactId: String) = guard {
        val calId = findCalendarId() ?: return@guard
        existingEvents(calId)[contactId]?.let { deleteEvent(it.id) }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private data class EventRow(val id: Long, val dtStart: Long, val title: String)

    private fun upsertInto(calId: Long, existing: Map<String, EventRow>, contact: Contact) {
        val parts = BirthdayDate.parse(contact.birthday!!) ?: return
        val start = BirthdayDate.toUtcStartMillis(parts)
        val title = "🎂 ${contact.displayName}"
        val row = existing[contact.id]
        when {
            row == null -> insertEvent(calId, contact.id, title, start)
            row.dtStart != start || row.title != title -> updateEvent(row.id, title, start)
            else -> Unit // unchanged
        }
    }

    private fun insertEvent(calId: Long, contactId: String, title: String, start: Long) {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calId)
            put(Events.TITLE, title)
            put(Events.DTSTART, start)
            put(Events.ALL_DAY, 1)
            put(Events.DURATION, "P1D")
            put(Events.EVENT_TIMEZONE, "UTC")
            put(Events.RRULE, "FREQ=YEARLY")
            put(Events.SYNC_DATA1, contactId) // maps event -> contact for reconciliation
        }
        resolver.insert(Events.CONTENT_URI.asSyncAdapter(), values)
    }

    private fun updateEvent(eventId: Long, title: String, start: Long) {
        val values = ContentValues().apply {
            put(Events.TITLE, title)
            put(Events.DTSTART, start)
            put(Events.DURATION, "P1D")
        }
        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null)
    }

    private fun deleteEvent(eventId: Long) {
        resolver.delete(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId).asSyncAdapter(),
            null,
            null,
        )
    }

    private fun existingEvents(calId: Long): Map<String, EventRow> {
        val cursor = resolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.SYNC_DATA1, Events.DTSTART, Events.TITLE),
            "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0",
            arrayOf(calId.toString()),
            null,
        ) ?: return emptyMap()
        return cursor.use {
            buildMap {
                while (it.moveToNext()) {
                    val contactId = it.getString(1) ?: continue
                    put(contactId, EventRow(it.getLong(0), it.getLong(2), it.getString(3) ?: ""))
                }
            }
        }
    }

    private fun findCalendarId(): Long? {
        val cursor = resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.ACCOUNT_NAME}=? AND ${Calendars.NAME}=?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, ACCOUNT_NAME, CALENDAR_NAME),
            null,
        ) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    private fun ensureCalendar(): Long? {
        findCalendarId()?.let { return it }
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, CALENDAR_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(Calendars.CALENDAR_COLOR, 0xFF7E57C2.toInt()) // matches the app's purple
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.VISIBLE, 1)
        }
        val uri = resolver.insert(Calendars.CONTENT_URI.asSyncAdapter(), values)
        return uri?.let { ContentUris.parseId(it) }
    }

    /** Local-account sync-adapter access is required to create calendars / set sync columns. */
    private fun android.net.Uri.asSyncAdapter(): android.net.Uri =
        buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

    private inline fun guard(block: () -> Unit) {
        runCatching { block() }
    }

    companion object {
        private const val ACCOUNT_NAME = "Hessible"
        private const val CALENDAR_NAME = "Hessible Birthdays"
    }
}
