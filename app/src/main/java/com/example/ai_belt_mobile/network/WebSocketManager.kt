package com.example.ai_belt_mobile.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.compareTo

sealed class WsEvent {
    object Opened : WsEvent()
    data class Message(val text: String) : WsEvent()
    data class Error(val throwable: Throwable) : WsEvent()
    data class Closed(val code: Int, val reason: String) : WsEvent()
}

object WebSocketManager {

    private const val WS_HOST = "ws://192.168.1.156:8080"

    private val connection: WebSocketContract = WebSocketConnection()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var heartbeatJob: Job? = null
    private var currentUserId: Int = -1
    private var currentIdentity: Int = -1

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    fun connect(userId: Int, identity: Int) {
        if (userId <= 0) return
        if (identity != 0 && identity != 1) return
        if (connection.isConnected()) return

        currentUserId = userId
        currentIdentity = identity

        val rolePath = if (identity == 0) "disability" else "family"
        val url = "$WS_HOST/ws/$rolePath/$userId"

        connection.connect(url, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                _events.tryEmit(WsEvent.Opened)
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _events.tryEmit(WsEvent.Message(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                stopHeartbeat()
                _events.tryEmit(WsEvent.Closed(code, reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                stopHeartbeat()
                _events.tryEmit(WsEvent.Closed(code, reason))
                tryReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                stopHeartbeat()
                _events.tryEmit(WsEvent.Error(t))
                tryReconnect()
            }
        })
    }

    fun send(text: String): Boolean = connection.send(text)

    fun sendPing(): Boolean {
        val json = JSONObject().put("type", "ping").toString()
        return send(json)
    }

    fun sendRequest(fromId: String, toId: String): Boolean {
        return sendEnvelope(
            type = "request",
            fromId = fromId,
            toId = toId,
            data = JSONObject()
        )
    }

    fun sendLocation(fromId: String, toId: String, longitude: String, latitude: String, time: String): Boolean {
        val data = JSONObject()
            .put("longitude", longitude)
            .put("latitude", latitude)
            .put("time", time)

        return sendEnvelope(
            type = "location",
            fromId = fromId,
            toId = toId,
            data = data
        )
    }

    fun sendSOS(fromId: String, toId: String?, longitude: String, latitude: String, time: String): Boolean {
        val root = JSONObject()
            .put("type", "SOS")
            .put("fromId", fromId)
            .put("timeStamp", System.currentTimeMillis())
            .put(
                "data",
                JSONObject()
                    .put("longitude", longitude)
                    .put("latitude", latitude)
                    .put("time", time)
            )

        if (!toId.isNullOrBlank()) {
            root.put("toId", toId)
        }
        return send(root.toString())
    }

    private fun sendEnvelope(type: String, fromId: String, toId: String, data: JSONObject): Boolean {
        val json = JSONObject()
            .put("type", type)
            .put("fromId", fromId)
            .put("toId", toId)
            .put("timeStamp", System.currentTimeMillis())
            .put("data", data)
            .toString()
        return send(json)
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive && _isConnected.value) {
                delay(30_000L)
                sendPing()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun tryReconnect() {
        // 如果当前没有用户信息就不重连
        if (currentUserId <= 0 || (currentIdentity != 0 && currentIdentity != 1)) {
            return
        }

        // 启动一个协程延时重连，避免立刻频繁重试
        scope.launch {
            // 简单写法：固定 5 秒后重连，你也可以改成指数退避
            delay(5_000L)

            // 如果在这段时间里已经被别处连上了，就不再重连
            if (!_isConnected.value && !connection.isConnected()) {
                connect(currentUserId, currentIdentity)
            }
        }
    }

    fun disconnect() {
        stopHeartbeat()
        connection.disconnect()
        _isConnected.value = false
    }
}