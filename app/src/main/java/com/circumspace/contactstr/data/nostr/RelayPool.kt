package com.circumspace.contactstr.data.nostr

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * A pool of relay WebSocket connections speaking the raw Nostr protocol. Connection state is
 * exposed as a flow; all inbound relay messages are merged into [incoming] for the SyncManager
 * to route. Knows nothing about events themselves.
 */
class RelayPool(private val urls: List<String>) {
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val sockets = HashMap<String, WebSocket>()

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    @Volatile
    private var onConnect: ((String) -> Unit)? = null

    fun setOnConnect(callback: (String) -> Unit) {
        onConnect = callback
    }

    fun connect() {
        urls.forEach { url ->
            val request = Request.Builder().url(url).build()
            sockets[url] = client.newWebSocket(request, listener(url))
        }
    }

    /** Send a raw message (e.g. an EVENT or REQ frame) to every connected relay. */
    fun send(text: String) {
        sockets.values.forEach { runCatching { it.send(text) } }
    }

    fun close() {
        sockets.values.forEach { runCatching { it.close(1000, null) } }
        sockets.clear()
        _connected.value = emptySet()
    }

    private fun listener(url: String) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connected.update { it + url }
            onConnect?.invoke(url)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _incoming.tryEmit(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            _connected.update { it - url }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connected.update { it - url }
        }
    }
}
