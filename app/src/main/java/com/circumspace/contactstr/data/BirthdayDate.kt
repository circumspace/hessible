package com.circumspace.contactstr.data

import java.util.Calendar

/**
 * Parsing/formatting for the [com.circumspace.contactstr.domain.Contact.birthday] string, which is
 * either a full "YYYY-MM-DD" or a year-less "--MM-DD". Year-less birthdays are common, so the year
 * is optional throughout.
 */
object BirthdayDate {
    /** A fixed leap year used as the placeholder when a birthday has no year (keeps Feb 29 valid). */
    private const val PLACEHOLDER_YEAR = 2000

    data class Parts(val year: Int?, val month: Int, val day: Int)

    /** Parse "YYYY-MM-DD" or "--MM-DD" into (year?, month 1-12, day 1-31), or null if malformed. */
    fun parse(value: String): Parts? {
        val s = value.trim()
        val (yearStr, mm, dd) = when {
            s.startsWith("--") -> {
                val rest = s.removePrefix("--").split("-")
                if (rest.size != 2) return null
                Triple(null, rest[0], rest[1])
            }
            else -> {
                val parts = s.split("-")
                if (parts.size != 3) return null
                Triple(parts[0], parts[1], parts[2])
            }
        }
        val year = yearStr?.toIntOrNull()
        val month = mm.toIntOrNull() ?: return null
        val day = dd.toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        if (yearStr != null && year == null) return null
        return Parts(year, month, day)
    }

    /** Build the canonical string; a null year yields the year-less "--MM-DD" form. */
    fun format(year: Int?, month: Int, day: Int): String =
        if (year == null) "--%02d-%02d".format(month, day)
        else "%04d-%02d-%02d".format(year, month, day)

    /**
     * Epoch millis at UTC midnight for the birthday, using [PLACEHOLDER_YEAR] when year-less — the
     * DTSTART anchor for an all-day, yearly-recurring calendar event.
     */
    fun toUtcStartMillis(parts: Parts): Long {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(parts.year ?: PLACEHOLDER_YEAR, parts.month - 1, parts.day, 0, 0, 0)
        }
        return cal.timeInMillis
    }

    /** Convert a UTC-midnight epoch-millis (as a DatePicker yields) to our canonical string. */
    fun fromUtcMillis(millis: Long, includeYear: Boolean): String {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        return format(
            if (includeYear) cal.get(Calendar.YEAR) else null,
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    /** Whether the stored value carries a year (for restoring the picker's "include year" toggle). */
    fun hasYear(value: String): Boolean = parse(value)?.year != null

    /** UTC-midnight millis for a stored value, or null — the picker's initial selection. */
    fun toUtcMillisOrNull(value: String): Long? = parse(value)?.let { toUtcStartMillis(it) }

    /** Human display, e.g. "23 April 1985" or "23 April" when year-less. */
    fun display(value: String): String {
        val p = parse(value) ?: return value
        val month = MONTHS.getOrNull(p.month - 1) ?: return value
        return if (p.year != null) "${p.day} $month ${p.year}" else "${p.day} $month"
    }

    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
}
