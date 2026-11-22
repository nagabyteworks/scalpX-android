package com.nagabyte.scalpx.data.remote

import com.nagabyte.scalpx.data.local.ConfigStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TradingWsClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeConfigStore: FakeConfigStore
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var wsClient: TradingWsClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val host = mockWebServer.hostName
        val port = mockWebServer.port
        fakeConfigStore = FakeConfigStore(
            baseUrl = "http://$host:$port",
            jwtToken = "test-ws-token",
            deviceId = "test-ws-device-id"
        )

        okHttpClient = OkHttpClient.Builder().build()
        wsClient = TradingWsClient(okHttpClient, fakeConfigStore)
    }

    @After
    fun tearDown() {
        runCatching { wsClient.disconnect() }
        runCatching { mockWebServer.shutdown() }
    }

    @Test
    fun `wsClient connects and receives 150ms TradingSnapshot`() = runBlocking {
        var serverWs: WebSocket? = null
        mockWebServer.enqueue(
            MockResponse()
                .withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        serverWs = webSocket
                        // Push 150 ms TradingSnapshot
                        webSocket.send("""
                            {
                                "type": "SNAPSHOT",
                                "timestamp": 1732174200150,
                                "openPositionsCount": 1,
                                "positions": [
                                    {
                                        "token": "26000",
                                        "symbol": "NIFTY26NOV24500CE",
                                        "quantity": 50,
                                        "entryPricePaise": 10000,
                                        "currentLtpPaise": 11150,
                                        "unrealizedPnlPaise": 57500,
                                        "nextMilestonePaise": 11500,
                                        "activeSlTriggerPaise": 11000,
                                        "activeSlLimitPaise": 10850,
                                        "isPaused": false,
                                        "status": "OPEN"
                                    }
                                ]
                            }
                        """.trimIndent())
                    }
                })
        )

        wsClient.connect()

        val snapshot = withTimeout(5000) {
            wsClient.snapshotFlow.first()
        }

        assertEquals("SNAPSHOT", snapshot.type)
        assertEquals(1, snapshot.openPositionsCount)
        assertEquals(1, snapshot.positions.size)
        val pos = snapshot.positions[0]
        assertEquals("26000", pos.token)
        assertEquals("NIFTY26NOV24500CE", pos.symbol)
        assertEquals(10000L, pos.entryPricePaise)
        assertEquals(11150L, pos.currentLtpPaise)
        assertEquals(57500L, pos.unrealizedPnlPaise)
        assertEquals(11500L, pos.nextMilestonePaise)
        assertEquals(11000L, pos.activeSlTriggerPaise)
        assertEquals(10850L, pos.activeSlLimitPaise)
    }

    @Test
    fun `wsClient emits high-priority events immediately`() = runBlocking {
        var serverWs: WebSocket? = null
        mockWebServer.enqueue(
            MockResponse()
                .withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        serverWs = webSocket
                        // Push SL_RATCHETED event
                        webSocket.send("""
                            {
                                "type": "SL_RATCHETED",
                                "timestamp": 1732174200200,
                                "data": {
                                    "token": "26000",
                                    "newTriggerPaise": 11500,
                                    "newLimitPaise": 11350
                                }
                            }
                        """.trimIndent())
                    }
                })
        )

        wsClient.connect()

        val event = withTimeout(5000) {
            wsClient.priorityEventFlow.first()
        }

        assertEquals("SL_RATCHETED", event.type)
        assertNotNull(event.data)
    }

    @Test
    fun `wsClient connects using headers only without query parameters`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .withWebSocketUpgrade(object : WebSocketListener() {})
        )

        wsClient.connect()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/ws", recordedRequest.path)
        assertEquals("Bearer test-ws-token", recordedRequest.getHeader("Authorization"))
        assertEquals("test-ws-device-id", recordedRequest.getHeader("X-Device-Id"))
    }

    @Test
    fun `wsClient safely handles unknown event types without crashing`() = runBlocking {
        var serverWs: WebSocket? = null
        mockWebServer.enqueue(
            MockResponse()
                .withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        serverWs = webSocket
                        // Push an unknown event type
                        webSocket.send("""
                            {
                                "type": "CUSTOM_UNKNOWN_BROKER_EVENT",
                                "timestamp": 1732174200500,
                                "data": { "foo": "bar" }
                            }
                        """.trimIndent())
                    }
                })
        )

        wsClient.connect()

        val event = withTimeout(5000) {
            wsClient.priorityEventFlow.first()
        }

        assertEquals("CUSTOM_UNKNOWN_BROKER_EVENT", event.type)
    }

    private class FakeConfigStore(
        private var baseUrl: String,
        private var jwtToken: String?,
        private var deviceId: String
    ) : ConfigStore {
        override fun getServerBaseUrl(): String = baseUrl
        override fun setServerBaseUrl(url: String) { baseUrl = url }
        override fun getJwtToken(): String? = jwtToken
        override fun setJwtToken(token: String?) { jwtToken = token }
        override fun getDeviceId(): String = deviceId
        override fun clearAuth() { jwtToken = null }
        override fun getDefaultLots(): Int = 2
        override fun setDefaultLots(lots: Int) {}
        override fun getFirstTargetPts(): Double = 10.0
        override fun setFirstTargetPts(pts: Double) {}
        override fun getStepPts(): Double = 5.0
        override fun setStepPts(pts: Double) {}
        override fun getBreakoutBufferPts(): Double = 1.0
        override fun setBreakoutBufferPts(pts: Double) {}
        override fun getSlippageBufferPts(): Double = 1.5
        override fun setSlippageBufferPts(pts: Double) {}
    }
}
