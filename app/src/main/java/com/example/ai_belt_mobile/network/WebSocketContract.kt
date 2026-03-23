package com.example.ai_belt_mobile.network

import okhttp3.WebSocketListener

interface WebSocketContract {

    fun connect(url: String, listener: WebSocketListener)
    fun send(text: String): Boolean
    fun isConnected(): Boolean
    fun disconnect()
}