package com.circumspace.contactstr.data

import com.circumspace.contactstr.domain.Contact

/**
 * Temporary seed data so the list isn't empty during shell development. This is removed once
 * contacts are loaded from the encrypted Room cache / relays.
 */
object SampleContacts {
    private val names = listOf(
        "Ada Lovelace", "Grace Hopper", "Alan Turing", "Linus Torvalds", "Margaret Hamilton",
        "Dennis Ritchie", "Katherine Johnson", "Edsger Dijkstra", "Barbara Liskov", "Tim Berners-Lee",
        "Radia Perlman", "Vint Cerf", "Donald Knuth", "Frances Allen", "Ken Thompson",
        "Hedy Lamarr", "Claude Shannon", "John von Neumann", "Annie Easley", "Guido van Rossum",
        "Bjarne Stroustrup", "James Gosling", "Brendan Eich", "Anders Hejlsberg", "Yukihiro Matsumoto",
        "Rich Hickey", "Joe Armstrong", "John Carmack", "Satoshi Nakamoto", "Hal Finney",
        "Adam Back", "Nick Szabo", "Gavin Wood", "Andreas Antonopoulos", "Jack Dorsey",
        "Will Casarin", "Vitor Pamplona", "Edward Snowden", "Phil Zimmermann", "Whitfield Diffie",
        "Martin Hellman", "Ron Rivest", "Adi Shamir", "Leonard Adleman", "Bruce Schneier",
        "Daniel Bernstein", "Moxie Marlinspike", "Peter Todd", "Jameson Lopp", "Gigi",
    )

    private val notes = mapOf(
        "Ada Lovelace" to "Wrote the first algorithm.",
        "Satoshi Nakamoto" to "Do not lose the keys.",
        "Vitor Pamplona" to "Built Amethyst & Quartz.",
        "Will Casarin" to "Damus / Nostr.",
        "Claude Shannon" to "Information theory.",
        "Moxie Marlinspike" to "Signal protocol.",
    )

    // A few contacts get richer fields so the detail view has addresses/websites/nostr to show.
    private val addresses = mapOf(
        "Ada Lovelace" to "12 St James's Square, London, UK",
        "Vitor Pamplona" to "1 Memorial Drive, Cambridge, MA",
        "Grace Hopper" to "Arlington National Cemetery, Arlington, VA",
    )
    private val websites = mapOf(
        "Vitor Pamplona" to "amethyst.social",
        "Will Casarin" to "damus.io",
        "Bruce Schneier" to "schneier.com",
        "Donald Knuth" to "cs.stanford.edu/~knuth",
    )
    private val nostrs = mapOf(
        "Vitor Pamplona" to "npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z",
        "Will Casarin" to "npub1xtscya34g58tk0z605fvr788k263gsu6cy9x0mhnm87echrgufzsevkk5s",
    )

    fun generate(): List<Contact> = names.mapIndexed { i, name ->
        val slug = name.lowercase().replace(Regex("[^a-z ]"), "").trim().replace(Regex("\\s+"), ".")
        Contact(
            displayName = name,
            phone = "+1 555 %04d".format(100 + i),
            email = "$slug@example.com",
            address = addresses[name].orEmpty(),
            website = websites[name].orEmpty(),
            nostr = nostrs[name].orEmpty(),
            note = notes[name].orEmpty(),
        )
    }
}
