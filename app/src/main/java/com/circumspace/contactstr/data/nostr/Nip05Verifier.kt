package com.circumspace.contactstr.data.nostr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Verifies NIP-05 identifiers (`name@domain`) by fetching the domain's `.well-known/nostr.json`
 * and confirming it maps the local part to the profile's pubkey. Results are cached in-memory so
 * repeated searches don't re-hit the network. This is real verification, not just "has a nip05".
 */
object Nip05Verifier {
    private val client = OkHttpClient.Builder()
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Boolean>()

    suspend fun verify(nip05: String, pubKeyHex: String): Boolean {
        val id = nip05.trim().lowercase()
        val at = id.indexOf('@')
        if (at <= 0 || at == id.length - 1) return false
        val name = id.substring(0, at)
        val domain = id.substring(at + 1)
        val key = "$id|$pubKeyHex"
        cache[key]?.let { return it }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://$domain/.well-known/nostr.json?name=$name"
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    val body = resp.body?.string() ?: return@runCatching false
                    val names = JSONObject(body).optJSONObject("names") ?: return@runCatching false
                    names.optString(name).equals(pubKeyHex, ignoreCase = true)
                }
            }.getOrDefault(false)
        }
        cache[key] = result
        return result
    }
}
