package com.circumspace.contactstr.data

import com.circumspace.contactstr.domain.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardIoTest {
    @Test
    fun exportThenImport_roundTripsAllFields() {
        val contacts = listOf(
            Contact(
                displayName = "Ada Lovelace",
                phone = "+1 555 0100",
                email = "ada@example.com",
                address = "12 St James's Square, London, UK",
                website = "ada.example.com",
                nostr = "npub1vf8gexcdyjk4l9qln5ama64qjj3aqgaff5v5mpp3mwd5kj448h7qkkj2s4",
                note = "First programmer.",
            ),
            Contact(displayName = "Bob Minimal", phone = "555-0001"),
        )

        val vcf = VCardIo.export(contacts)
        assertTrue("should be a vCard", vcf.contains("BEGIN:VCARD"))
        assertTrue("should be v3.0", vcf.contains("VERSION:3.0"))

        val parsed = VCardIo.parse(vcf.byteInputStream())
        assertEquals(2, parsed.size)

        val ada = parsed.first { it.displayName == "Ada Lovelace" }
        assertEquals("+1 555 0100", ada.phone)
        assertEquals("ada@example.com", ada.email)
        assertEquals("ada.example.com", ada.website)
        assertEquals("npub1vf8gexcdyjk4l9qln5ama64qjj3aqgaff5v5mpp3mwd5kj448h7qkkj2s4", ada.nostr)
        assertEquals("First programmer.", ada.note)
        assertTrue("address preserved", ada.address.contains("St James"))

        val bob = parsed.first { it.displayName == "Bob Minimal" }
        assertEquals("555-0001", bob.phone)
        assertEquals("", bob.email)
    }
}
