package com.circumspace.contactstr.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasteImportTest {
    @Test
    fun parsesNamePhoneEmail_inAnyOrder() {
        val parsed = PasteImport.parse("Anna Schmidt, +49-30-1234567, anna@example.com").single()
        val c = parsed.contact!!
        assertEquals("Anna Schmidt", c.displayName)
        assertEquals("+49-30-1234567", c.phone)
        assertEquals("anna@example.com", c.email)

        val reordered = PasteImport.parse("anna@example.com, +49-30-1234567, Anna Schmidt").single()
        assertEquals("Anna Schmidt", reordered.contact!!.displayName)
        assertEquals("anna@example.com", reordered.contact!!.email)
    }

    @Test
    fun nameOnly_isValid() {
        val c = PasteImport.parse("Bob Kahn").single().contact!!
        assertEquals("Bob Kahn", c.displayName)
        assertEquals("", c.phone)
        assertEquals("", c.email)
    }

    @Test
    fun multipleLines_blankLinesSkipped() {
        val parsed = PasteImport.parse(
            """
            Anna Schmidt, +49 30 1234567

            Bob Kahn, bob@example.com
            """.trimIndent(),
        )
        assertEquals(2, parsed.size)
        assertEquals("Anna Schmidt", parsed[0].contact!!.displayName)
        assertEquals("bob@example.com", parsed[1].contact!!.email)
    }

    @Test
    fun phoneVariants_recognized() {
        listOf("+1 (555) 010-0000", "030/1234567", "+49-171-2345678", "0171 2345678").forEach {
            val c = PasteImport.parse("Test Person, $it").single().contact!!
            assertEquals("phone not recognized: $it", it, c.phone)
        }
    }

    @Test
    fun lineWithoutName_isFlaggedNotDropped() {
        val parsed = PasteImport.parse("+49-30-1234567, anna@example.com").single()
        assertNull(parsed.contact)
        assertEquals("+49-30-1234567, anna@example.com", parsed.raw)
    }

    @Test
    fun firstPhoneAndEmailWin_extrasJoinName() {
        val c = PasteImport.parse("Dr. Jane Doe, MD, +1 555 0100, jane@example.com").single().contact!!
        assertEquals("Dr. Jane Doe MD", c.displayName)
        assertEquals("+1 555 0100", c.phone)
    }

    @Test
    fun colonAndFreeFormSeparators_work() {
        // The reported real-world case: colon-separated business entry.
        val vet = PasteImport.parse("Tierarzt Hennen Schlüchtern: +49 6661 2339").single().contact!!
        assertEquals("Tierarzt Hennen Schlüchtern", vet.displayName)
        assertEquals("+49 6661 2339", vet.phone)

        val noSeparator = PasteImport.parse("Maria Weber +49 170 5551234 maria@web.de").single().contact!!
        assertEquals("Maria Weber", noSeparator.displayName)
        assertEquals("+49 170 5551234", noSeparator.phone)
        assertEquals("maria@web.de", noSeparator.email)

        val semicolons = PasteImport.parse("Jan Novak; jan@example.org; +420 601 123 456").single().contact!!
        assertEquals("Jan Novak", semicolons.displayName)
        assertEquals("+420 601 123 456", semicolons.phone)
    }

    @Test
    fun shortNumbersAreNotPhones() {
        // "42" is name-like, not a phone (too few digits) — ends up in the name.
        val c = PasteImport.parse("Agent 42, +1 555 010 9999").single().contact!!
        assertEquals("Agent 42", c.displayName)
        assertEquals("+1 555 010 9999", c.phone)
    }
}
