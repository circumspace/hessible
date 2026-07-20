package com.circumspace.contactstr.data.nostr

import com.circumspace.contactstr.data.LOCAL_RELAY_URL
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Detects an on-device Nostr relay (e.g. Citrine) listening on [LOCAL_RELAY_URL]. Returns true if
 * a WebSocket handshake to the loopback relay succeeds within the timeout. Requires cleartext
 * traffic to 127.0.0.1 to be permitted (see network_security_config.xml).
 */
object LocalRelayProbe {
    private val client = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    suspend fun isPresent(url: String = LOCAL_RELAY_URL): Boolean =
        withTimeoutOrNull(2000) { probe(url) } ?: false

    private suspend fun probe(url: String): Boolean = suspendCancellableCoroutine { cont ->
        val ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.close(1000, null)
                    if (cont.isActive) cont.resume(true)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
        cont.invokeOnCancellation { runCatching { ws.cancel() } }
    }
}
