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
                        "brokerMode": "live",
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
        assertEquals("live", health.brokerMode)
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
    fun `dailyLogin submits OTP and receives DailyLoginResponse`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "status": "ok",
                        "message": "Daily Flattrade authentication successful",
                        "brokerConnected": true,
                        "tradeDate": "2026-11-21",
                        "tokenGeneratedAt": "2026-11-21T09:15:00.000Z"
                    }
                """.trimIndent())
        )

        val response = tradingApi.dailyLogin(DailyLoginRequest(otp = "123456"))
        assertTrue(response.isSuccessful)
        val body = response.body()
        assertNotNull(body)
        assertEquals(true, body?.brokerConnected)
        assertEquals("2026-11-21", body?.tradeDate)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/auth/daily-login", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer test-jwt-token-xyz", recorded.getHeader("Authorization"))
        assertEquals("test-device-uuid-1234", recorded.getHeader("X-Device-Id"))
    }

    @Test
    fun `provisionDevice sends enrollmentSecret and receives device-bound JWT`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                        "status": "ok",
                        "token": "device-bound-jwt-9999",
                        "deviceId": "test-device-uuid-1234",
                        "expiresAt": 1735000000000
                    }
                """.trimIndent())
        )

        val response = tradingApi.provisionDevice(
            ProvisionDeviceRequest(
                enrollmentSecret = "owner-enrollment-secret-xyz",
                deviceName = "Android Trading Phone"
            )
        )

        assertTrue(response.isSuccessful)
        val body = response.body()
        assertNotNull(body)
        assertEquals("ok", body?.status)
        assertEquals("device-bound-jwt-9999", body?.token)
        assertEquals("test-device-uuid-1234", body?.deviceId)
        assertEquals(1735000000000L, body?.expiresAt)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/auth/provision-device", recorded.path)
        assertEquals("POST", recorded.method)
        val bodyStr = recorded.body.readUtf8()
        assertTrue(bodyStr.contains("\"enrollmentSecret\":\"owner-enrollment-secret-xyz\""))
        assertTrue(bodyStr.contains("\"deviceName\":\"Android Trading Phone\""))
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
                        "brokerMode": "paper",
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
        assertEquals("paper", health.brokerMode)
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
