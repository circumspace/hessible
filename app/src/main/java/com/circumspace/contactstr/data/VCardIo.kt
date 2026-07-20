package com.circumspace.contactstr.data

import com.circumspace.contactstr.domain.Contact
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.property.Address
import ezvcard.property.FormattedName
import ezvcard.property.StructuredName
import java.io.InputStream

/**
 * Contact ↔ vCard (.vcf) conversion via ez-vcard. Exports vCard 3.0 for broad compatibility with
 * stock contacts apps; imports any version ez-vcard understands (2.1 / 3.0 / 4.0).
 *
 * The Nostr identity field has no standard vCard property, so it round-trips through the
 * `X-NOSTR` extended property. Photos are not yet included (local URIs aren't portable).
 */
object VCardIo {
    private const val X_NOSTR = "X-NOSTR"

    fun export(contacts: List<Contact>): String {
        val cards = contacts.map { c ->
            VCard().apply {
                setFormattedName(FormattedName(c.displayName))
                val parts = c.displayName.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    structuredName = StructuredName().apply {
                        given = parts.first()
                        family = parts.drop(1).joinToString(" ")
                    }
                }
                if (c.phone.isNotBlank()) addTelephoneNumber(c.phone)
                if (c.email.isNotBlank()) addEmail(c.email)
                if (c.address.isNotBlank()) addAddress(Address().apply { streetAddress = c.address })
                if (c.website.isNotBlank()) addUrl(c.website)
                if (c.note.isNotBlank()) addNote(c.note)
                if (c.nostr.isNotBlank()) addExtendedProperty(X_NOSTR, c.nostr)
            }
        }
        return Ezvcard.write(cards).version(VCardVersion.V3_0).go()
    }

    fun parse(input: InputStream): List<Contact> {
        val cards: List<VCard> = Ezvcard.parse(input).all()
        return cards.mapNotNull { v ->
            val name = v.formattedName?.value
                ?: v.structuredName?.let { listOfNotNull(it.given, it.family).joinToString(" ").trim() }
                ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null
            Contact(
                displayName = name,
                phone = v.telephoneNumbers.firstOrNull()?.let { it.text ?: it.uri?.number } ?: "",
                email = v.emails.firstOrNull()?.value ?: "",
                address = v.addresses.firstOrNull()?.let { addr ->
                    listOfNotNull(addr.streetAddress, addr.locality, addr.region, addr.postalCode, addr.country)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                        .ifBlank { null }
                } ?: "",
                website = v.urls.firstOrNull()?.value ?: "",
                nostr = v.getExtendedProperty(X_NOSTR)?.value ?: "",
                note = v.notes.firstOrNull()?.value ?: "",
            )
        }
    }
}
