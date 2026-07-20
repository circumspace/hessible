package com.circumspace.contactstr.data.blob

import android.util.Base64
import com.circumspace.contactstr.crypto.NostrIdentity
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Uploads an (already encrypted) blob to one or more Blossom servers per BUD-01/02: a signed
 * kind-24242 authorization event travels in the `Authorization: Nostr <base64>` header, and the
 * blob is `PUT`. The server addresses the blob by the sha256 in the `x` tag, which MUST match the
 * exact bytes uploaded — so we hash the ciphertext, not the plaintext image.
 *
 * Uploading to several servers gives the photo redundancy: display later tries each mirror URL.
 */
class BlossomBlobStore(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val authKind = 24242
    private val authTtlSeconds = 300L

    /**
     * Upload [ciphertext] (sha256 = [sha256Hex]) to every server in [servers]; returns the URLs of
     * the mirrors that accepted it (empty if all failed). [identity] signs the auth event.
     */
    suspend fun upload(
        ciphertext: ByteArray,
        sha256Hex: String,
        servers: List<String>,
        identity: NostrIdentity,
    ): List<String> {
        if (servers.isEmpty()) return emptyList()
        val authHeader = "Nostr " + buildAuthEventBase64(sha256Hex, identity)

        return servers.mapNotNull { server ->
            runCatching { putToServer(server.trimEnd('/'), ciphertext, sha256Hex, authHeader) }.getOrNull()
        }
    }

    private suspend fun buildAuthEventBase64(sha256Hex: String, identity: NostrIdentity): String {
        val expiration = nowSec() + authTtlSeconds
        val tags = arrayOf(
            arrayOf("t", "upload"),
            arrayOf("x", sha256Hex),
            arrayOf("expiration", expiration.toString()),
        )
        val event: Event = identity.signer.sign(nowSec(), authKind, tags, "Upload contact photo")
        return Base64.encodeToString(event.toJson().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /** PUT to one server; returns the blob URL it reports, falling back to `<server>/<sha256>`. */
    private suspend fun putToServer(
        server: String,
        ciphertext: ByteArray,
        sha256Hex: String,
        authHeader: String,
    ): String? = withContext(Dispatchers.IO) {
        val body = ciphertext.toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url("$server/upload")
            .put(body)
            .header("Authorization", authHeader)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val reported = resp.body?.string()?.let {
                runCatching { JSONObject(it).optString("url").ifBlank { null } }.getOrNull()
            }
            reported ?: "$server/$sha256Hex"
        }
    }

    private fun nowSec() = System.currentTimeMillis() / 1000
}
