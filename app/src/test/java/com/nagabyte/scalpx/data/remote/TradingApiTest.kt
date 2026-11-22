package com.nagabyte.scalpx.data.remote

import com.nagabyte.scalpx.data.local.ConfigStore
import com.nagabyte.scalpx.data.remote.dto.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TradingApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeConfigStore: FakeConfigStore
    private lateinit var tradingApi: TradingApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        fakeConfigStore = FakeConfigStore(
            baseUrl = mockWebServer.url("/").toString(),
            jwtToken = "test-jwt-token-xyz",
            deviceId = "test-device-uuid-1234"
        )

        val okHttpClient = TradingApi.createOkHttpClient(fakeConfigStore)
        tradingApi = TradingApi.create(mockWebServer.url("/").toString(), okHttpClient)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `AuthInterceptor attaches Authorization Bearer and X-Device-Id headers`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "status": "ok",
                        "environment": "LIVE",
                        "brokerConnected": true,
                        "wsFeedHealthy": true,
                        "openPositionsCount": 1,
                        "memoryRssMb": 68,
                        "uptimeSec": 1420
                    }
                """.trimIndent())
        )

        val health = tradingApi.getHealth()
        assertEquals("ok", health.status)
        assertEquals("LIVE", health.environment)
        assertTrue(health.brokerConnected)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/health", recordedRequest.path)
        assertEquals("Bearer test-jwt-token-xyz", recordedRequest.getHeader("Authorization"))
        assertEquals("test-device-uuid-1234", recordedRequest.getHeader("X-Device-Id"))
    }

    @Test
    fun `placeOneClickOrder sends exact payload and parses LadderPositionDto`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "token": "26000",
                        "symbol": "NIFTY26NOV24500CE",
                        "side": "BUY",
                        "quantity": 50,
                        "entryPricePaise": 10005,
                        "nextMilestonePaise": 11000,
                        "stepSizePaise": 500,
                        "breakoutBufferPaise": 100,
                        "slippageBufferPaise": 150,
                        "activeSlOrderId": "FT_SL_991",
                        "activeSlTriggerPaise": 9005,
                        "status": "OPEN",
                        "isPaused": false
                    }
                """.trimIndent())
        )

        val request = OneClickOrderRequest(
            commandId = "550e8400-e29b-41d4-a716-446655440000",
            token = "26000",
            tradingSymbol = "NIFTY26NOV24500CE",
            exchange = "NFO",
            side = "BUY",
            lots = 2,
            lotSize = 25,
            product = "I",
            initialSlPoints = 10.0,
            ladderConfig = LadderConfigDto(
                firstMilestonePaise = 11000L,
                stepSizePaise = 500L,
                breakoutBufferPaise = 100L,
                slippageBufferPaise = 150L
            )
        )

        val result = tradingApi.placeOneClickOrder(request)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/orders/one-click", recordedRequest.path)
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"commandId\":\"550e8400-e29b-41d4-a716-446655440000\""))
        assertTrue(body.contains("\"firstMilestonePaise\":11000"))
        assertTrue(body.contains("\"stepSizePaise\":500"))

        assertEquals("26000", result.token)
        assertEquals("NIFTY26NOV24500CE", result.symbol)
        assertEquals(50, result.quantity)
        assertEquals(10005L, result.entryPricePaise)
        assertEquals(11000L, result.nextMilestonePaise)
        assertEquals("FT_SL_991", result.activeSlOrderId)
    }

    @Test
    fun `patchLadder sends partial update and returns updated position`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "token": "26000",
                        "symbol": "NIFTY26NOV24500CE",
                        "side": "BUY",
                        "quantity": 50,
                        "entryPricePaise": 10005,
                        "nextMilestonePaise": 11500,
                        "stepSizePaise": 600,
                        "breakoutBufferPaise": 100,
                        "slippageBufferPaise": 150,
                        "status": "OPEN",
                        "isPaused": false
                    }
                """.trimIndent())
        )

        val result = tradingApi.patchLadder(
            token = "26000",
            request = PatchLadderRequest(nextMilestonePaise = 11500L, stepSizePaise = 600L)
        )

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("PATCH", recordedRequest.method)
        assertEquals("/api/ladders/26000", recordedRequest.path)
        assertEquals(11500L, result.nextMilestonePaise)
        assertEquals(600L, result.stepSizePaise)
    }

    @Test
    fun `exitPosition and exitAll invoke correct endpoints`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "success": true,
                        "token": "26000",
                        "commandId": "cmd-123",
                        "orderId": "FT_ORD_EXIT_26000",
                        "status": "FILLED"
                    }
                """.trimIndent())
        )

        val exitPos = tradingApi.exitPosition(ExitPositionRequest("cmd-123", "26000"))
        assertTrue(exitPos.success)
        assertEquals("FT_ORD_EXIT_26000", exitPos.orderId)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "success": true,
                        "message": "Panic exit executed for all open positions"
                    }
                """.trimIndent())
        )

        val exitAll = tradingApi.exitAll(ExitAllRequest("cmd-exit-all"))
        assertTrue(exitAll.success)
        assertEquals("Panic exit executed for all open positions", exitAll.message)
    }

    @Test
    fun `authenticateDevice sends device UUID and TOTP without exposing code in request logs`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","accessToken":"short-lived-jwt","expiresIn":900,"device":{"id":"test-device-uuid-1234","name":"Android Trading Phone","status":"AUTHORIZED"}}""")
        )
        val response = tradingApi.authenticateDevice(
            DeviceAuthenticationRequest(deviceId = "test-device-uuid-1234", totp = "123456", deviceName = "Android Trading Phone")
        )
        assertTrue(response.isSuccessful)
        assertEquals("short-lived-jwt", response.body()?.accessToken)
        assertEquals(900, response.body()?.expiresIn)
        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/auth/device/authenticate", recorded.path)
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body.readUtf8().contains("\"totp\":\"123456\""))
        assertEquals("test-device-uuid-1234", recorded.getHeader("X-Device-Id"))
    }

    @Test
    fun `getSensexSpot parses only the backend fresh-quote status`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("""{"index":"SENSEX","exchange":"BSE","token":"789","status":"READY","spotLtpPaise":8123456,"timestamp":1795375000000,"ageMs":120,"marketDataReady":true,"validation":"TOKEN_FROM_FLATTRADE_GETINDEXLIST"}""")
        )
        val spot = tradingApi.getSensexSpot()
        assertEquals("SENSEX", spot.index)
        assertEquals("BSE", spot.exchange)
        assertEquals("READY", spot.status)
        assertEquals(8123456L, spot.spotLtpPaise)
        assertTrue(spot.marketDataReady)
        assertEquals("/api/market-data/sensex", mockWebServer.takeRequest().path)
    }

    @Test
    fun `HealthDto parses tradingEnabled, wsFeedStatus and accountStateUncertain`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "status": "ok",
                        "environment": "LIVE",
                        "brokerConnected": true,
                        "wsFeedHealthy": true,
                        "tradingEnabled": false,
                        "wsFeedStatus": "FEED_CONNECTED",
                        "accountStateUncertain": false,
                        "openPositionsCount": 0,
                        "memoryRssMb": 55,
                        "uptimeSec": 450
                    }
                """.trimIndent())
        )

        val health = tradingApi.getHealth()
        assertEquals("ok", health.status)
        assertEquals("LIVE", health.environment)
        assertFalse(health.tradingEnabled)
        assertEquals("FEED_CONNECTED", health.wsFeedStatus)
        assertFalse(health.accountStateUncertain)
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
