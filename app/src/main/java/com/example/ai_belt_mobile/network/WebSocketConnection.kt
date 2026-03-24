package com.example.ai_belt_mobile.network

import android.content.Context
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WebSocketConnection : WebSocketContract {

    private var webSocket: WebSocket? = null
    private val connected = AtomicBoolean(false)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun connect(url: String, listener: WebSocketListener) {
        connected.set(false)
        webSocket?.cancel()

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                connected.set(true)
                listener.onOpen(ws, response)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                listener.onMessage(ws, text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                connected.set(false)
                listener.onClosing(ws, code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                connected.set(false)
                listener.onClosed(ws, code, reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                connected.set(false)
                listener.onFailure(ws, t, response)
            }
        })
    }

    override fun send(text: String): Boolean {
        val ws = webSocket ?: return false
        if (!connected.get()) return false
        return ws.send(text)
    }

    override fun isConnected(): Boolean = connected.get()

    override fun disconnect() {
        connected.set(false)
        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }
}