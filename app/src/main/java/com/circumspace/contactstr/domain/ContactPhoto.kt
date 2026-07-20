package com.circumspace.contactstr.domain

/**
 * An encrypted contact photo stored on one or more Blossom servers.
 *
 * The blob at each [urls] entry is AES-256-GCM **ciphertext**, addressed by [sha256] (the hash of
 * that ciphertext — Blossom's content address). The symmetric [key] and [nonce] (both hex) decrypt
 * it back to a [mime] image. This whole descriptor lives inside the already NIP-44-encrypted
 * kind-30078 contact event, so the key never leaves encrypted storage — a plain Blossom URL leaks
 * nothing without the event.
 *
 * Multiple [urls] are mirrors of the *same* blob (same [sha256]); display tries them in order.
 */
data class ContactPhoto(
    val urls: List<String>,
    val sha256: String,
    val key: String,
    val nonce: String,
    val mime: String,
)
