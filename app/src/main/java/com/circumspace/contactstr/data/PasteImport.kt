package com.circumspace.contactstr.data

import com.circumspace.contactstr.domain.Contact

/**
 * Primitive free-text contact parsing for paste-import: one contact per line. Rather than
 * requiring a specific separator, the email and phone number are *extracted* from anywhere in the
 * line by shape (so "Name: +49 6661 2339", "Name, +49…", or "Name +49…" all work); whatever
 * remains — minus leftover separators — is the name. Lines with no usable name come back with
 * [ParsedLine.contact] == null so the UI can flag them instead of silently dropping input.
 */
object PasteImport {
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("\\+?\\d[\\d ()/\\-]{3,}\\d")
    private val SEPARATORS = Regex("[,;:\\t|]")
    private val WHITESPACE = Regex("\\s+")

    data class ParsedLine(val raw: String, val contact: Contact?)

    fun parse(text: String): List<ParsedLine> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> ParsedLine(line, parseLine(line)) }

    private fun parseLine(line: String): Contact? {
        var rest = line

        val email = EMAIL.find(rest)?.value.orEmpty()
        if (email.isNotEmpty()) rest = rest.replaceFirst(email, " ")

        // First digit-run that has enough digits to plausibly be a phone number (≥5 keeps things
        // like "Agent 42" or house numbers in the name).
        val phone = PHONE.findAll(rest)
            .map { it.value.trim() }
            .firstOrNull { candidate -> candidate.count { it.isDigit() } >= 5 }
            .orEmpty()
        if (phone.isNotEmpty()) rest = rest.replaceFirst(phone, " ")

        val name = rest
            .replace(SEPARATORS, " ")
            .replace(WHITESPACE, " ")
            .trim()
        if (name.isBlank()) return null
        return Contact(displayName = name, phone = phone, email = email)
    }
}
