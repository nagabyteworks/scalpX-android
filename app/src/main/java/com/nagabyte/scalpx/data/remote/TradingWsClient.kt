package com.nagabyte.scalpx.data.remote

import android.util.Log
import com.nagabyte.scalpx.BuildConfig
import com.nagabyte.scalpx.data.local.ConfigStore
import com.nagabyte.scalpx.data.remote.dto.PriorityEventMessage
import com.nagabyte.scalpx.data.remote.dto.TradingSnapshotMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

sealed class WsConnectionState {
    object Disconnected : WsConnectionState()
    object Connecting : WsConnectionState()
    data class Connected(val latencyMs: Long = 0L) : WsConnectionState()
    data class Failed(val error: Throwable) : WsConnectionState()
}

/**
 * OkHttp WebSocket client connecting to /ws.
 * Invariant: Consumes 150 ms throttled TradingSnapshot + immediate priority events.
 * Features:
 *  - 5-second ping interval
 *  - Round-trip latency tracking (pingMs)
 *  - Exponential backoff auto-reconnect (1s to 16s)
 *  - Network switch reconnect support
 */
class TradingWsClient(
    private val okHttpClient: OkHttpClient,
    private val configStore: ConfigStore,
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
) {
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _snapshotFlow = MutableSharedFlow<TradingSnapshotMessage>(replay = 1)
    val snapshotFlow: SharedFlow<TradingSnapshotMessage> = _snapshotFlow

    private val _priorityEventFlow = MutableSharedFlow<PriorityEventMessage>(extraBufferCapacity = 32)
    val priorityEventFlow: SharedFlow<PriorityEventMessage> = _priorityEventFlow

    private val _connectionState = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val connectionState: StateFlow<WsConnectionState> = _connectionState

    private val _snapshotAgeMs = MutableStateFlow(0L)
    val snapshotAgeMs: StateFlow<Long> = _snapshotAgeMs
    val pingMs: StateFlow<Long> = _snapshotAgeMs

    private var reconnectJob: Job? = null
    private var isExplicitDisconnect = false
    private var backoffDelayMs = INITIAL_BACKOFF_MS

    fun connect() {
        isExplicitDisconnect = false
        reconnectJob?.cancel()

        val wsUrl = ServerConfig.toWebSocketUrl("${configStore.getServerBaseUrl().trimEnd('/')}/ws")
        val request = Request.Builder().url(wsUrl).build()

        _connectionState.value = WsConnectionState.Connecting

        val client = okHttpClient.newBuilder()
            .pingInterval(PING_INTERVAL_SEC, TimeUnit.SECONDS)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                backoffDelayMs = INITIAL_BACKOFF_MS
                _connectionState.value = WsConnectionState.Connected(_snapshotAgeMs.value)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    val root = try {
                        json.parseToJsonElement(text) as? JsonObject
                    } catch (_: Exception) {
                        null
                    }
                    val messageType = root?.get("type")
                        ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
                    if (messageType == "SNAPSHOT") {
                        try {
                            val snapshot = json.decodeFromString<TradingSnapshotMessage>(text)
                            val now = System.currentTimeMillis()
                            if (snapshot.timestamp > 0) {
                                val age = (now - snapshot.timestamp).coerceAtLeast(0L)
                                _snapshotAgeMs.value = age
                                _connectionState.value = WsConnectionState.Connected(age)
                            }
                            _snapshotFlow.emit(snapshot)
                        } catch (_: Exception) {
                            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to parse backend snapshot")
                        }
                    } else if (messageType != null) {
                        try {
                            val event = json.decodeFromString<PriorityEventMessage>(text)
                            _priorityEventFlow.emit(event)
                        } catch (_: Exception) {
                            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to parse backend event type=$messageType")
                        }
                    } else if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Ignored backend WebSocket frame without a message type")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = WsConnectionState.Disconnected
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = WsConnectionState.Disconnected
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = WsConnectionState.Failed(t)
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (isExplicitDisconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffDelayMs)
            backoffDelayMs = min(backoffDelayMs * 2, MAX_BACKOFF_MS)
            if (!isExplicitDisconnect) {
                connect()
            }
        }
    }

    fun onNetworkAvailable() {
        if (!isExplicitDisconnect && _connectionState.value !is WsConnectionState.Connected) {
            backoffDelayMs = INITIAL_BACKOFF_MS
            connect()
        }
    }

    fun disconnect() {
        isExplicitDisconnect = true
        reconnectJob?.cancel()
        webSocket?.cancel()
        webSocket = null
        _connectionState.value = WsConnectionState.Disconnected
    }

    companion object {
        private const val TAG = "TradingWsClient"
        private const val PING_INTERVAL_SEC = 5L
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 16000L
    }
}
