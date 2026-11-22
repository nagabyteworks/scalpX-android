package com.nagabyte.scalpx.ui.scalper

import com.nagabyte.scalpx.data.local.ConfigStore
import com.nagabyte.scalpx.data.remote.TradingApi
import com.nagabyte.scalpx.data.remote.TradingWsClient
import com.nagabyte.scalpx.data.remote.dto.*
import com.nagabyte.scalpx.data.repository.ApiResult
import com.nagabyte.scalpx.data.repository.TradingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ScalperViewModelOtpTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeOtpTradingRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeOtpTradingRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `entering 6 digits automatically triggers submitDailyOtp exactly once`() = runTest(testDispatcher) {
        val viewModel = ScalperViewModel(repository = fakeRepo)
        testScheduler.advanceUntilIdle()

        // 1. Open Daily OTP Sheet
        viewModel.handleIntent(ScalperIntent.OpenDailyOtpSheet)
        testScheduler.advanceUntilIdle()
        assertTrue("Sheet must be visible", viewModel.uiState.value.dailyOtpSheet.isVisible)

        // 2. Input digits 1 to 5 (should NOT trigger submission)
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("1"))
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("12"))
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("123"))
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("1234"))
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("12345"))
        testScheduler.advanceUntilIdle()

        assertEquals("No submission before 6 digits", 0, fakeRepo.submitOtpCallCount.get())
        assertEquals("12345", viewModel.uiState.value.dailyOtpSheet.otpDigits)

        // 3. Input 6th digit -> triggers auto-submit exactly once
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("123456"))
        testScheduler.advanceUntilIdle()

        assertEquals("Auto-submits exactly once upon reaching 6 digits", 1, fakeRepo.submitOtpCallCount.get())
        assertEquals("123456", fakeRepo.lastSubmittedOtp)
    }

    @Test
    fun `successful login clears otpDigits, closes the sheet, and triggers option-chain refresh plus WS reconnect`() = runTest(testDispatcher) {
        fakeRepo.otpResponse = ApiResult.Success(
            DailyLoginResponse(
                brokerConnected = true,
                tradeDate = "2026-11-21",
                tokenGeneratedAt = "2026-11-21T09:15:00.000Z"
            )
        )

        val viewModel = ScalperViewModel(repository = fakeRepo)
        testScheduler.advanceUntilIdle()

        val initialOptionChainCalls = fakeRepo.optionChainCallCount.get()
        val initialWsConnectCalls = fakeRepo.wsConnectCallCount.get()

        viewModel.handleIntent(ScalperIntent.OpenDailyOtpSheet)
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("123456"))
        testScheduler.advanceUntilIdle()

        // 1. Sheet must be closed and OTP cleared
        assertFalse("Sheet must close on success", viewModel.uiState.value.dailyOtpSheet.isVisible)
        assertEquals("OTP digits must be cleared on success", "", viewModel.uiState.value.dailyOtpSheet.otpDigits)
        assertNull("Error message must be null on success", viewModel.uiState.value.dailyOtpSheet.errorMessage)

        // 2. Triggers WS reconnect, health check, and option chain refresh
        assertTrue("WS connect must be triggered on login success", fakeRepo.wsConnectCallCount.get() > initialWsConnectCalls)
        assertTrue("Option chain refresh must be triggered on login success", fakeRepo.optionChainCallCount.get() > initialOptionChainCalls)
    }

    @Test
    fun `401 INVALID_OTP clears otpDigits and displays the server error message`() = runTest(testDispatcher) {
        fakeRepo.otpResponse = ApiResult.Error(
            message = "Flattrade authentication failed: Invalid OTP or session expired",
            statusCode = 401,
            errorCode = "INVALID_OTP"
        )

        val viewModel = ScalperViewModel(repository = fakeRepo)
        testScheduler.advanceUntilIdle()

        viewModel.handleIntent(ScalperIntent.OpenDailyOtpSheet)
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("999999"))
        testScheduler.advanceUntilIdle()

        // Sheet stays open, otpDigits cleared, error message displayed
        assertTrue("Sheet must stay open on failure", viewModel.uiState.value.dailyOtpSheet.isVisible)
        assertEquals("otpDigits must be cleared on INVALID_OTP for immediate re-entry", "", viewModel.uiState.value.dailyOtpSheet.otpDigits)
        assertEquals(
            "Server error message must be displayed",
            "Flattrade authentication failed: Invalid OTP or session expired",
            viewModel.uiState.value.dailyOtpSheet.errorMessage
        )
        assertFalse("isSubmitting must reset to false", viewModel.uiState.value.dailyOtpSheet.isSubmitting)
    }

    @Test
    fun `502 FALLBACK_REQUIRED sets suggestBrowserFallback to true`() = runTest(testDispatcher) {
        fakeRepo.otpResponse = ApiResult.Error(
            message = "Flattrade requires browser authentication (CAPTCHA or changed flow)",
            statusCode = 502,
            errorCode = "FALLBACK_REQUIRED"
        )

        val viewModel = ScalperViewModel(repository = fakeRepo)
        testScheduler.advanceUntilIdle()

        viewModel.handleIntent(ScalperIntent.OpenDailyOtpSheet)
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("888888"))
        testScheduler.advanceUntilIdle()

        assertTrue("Sheet must remain visible", viewModel.uiState.value.dailyOtpSheet.isVisible)
        assertTrue("suggestBrowserFallback must be true", viewModel.uiState.value.dailyOtpSheet.suggestBrowserFallback)
        assertEquals(
            "Flattrade requires browser authentication (CAPTCHA or changed flow)",
            viewModel.uiState.value.dailyOtpSheet.errorMessage
        )
    }

    @Test
    fun `dismissing daily OTP sheet clears otpDigits and error message`() = runTest(testDispatcher) {
        val viewModel = ScalperViewModel(repository = fakeRepo)
        testScheduler.advanceUntilIdle()

        viewModel.handleIntent(ScalperIntent.OpenDailyOtpSheet)
        viewModel.handleIntent(ScalperIntent.UpdateOtpDigits("123"))
        assertEquals("123", viewModel.uiState.value.dailyOtpSheet.otpDigits)

        viewModel.handleIntent(ScalperIntent.DismissDailyOtpSheet)
        testScheduler.advanceUntilIdle()

        assertFalse("Sheet must be closed", viewModel.uiState.value.dailyOtpSheet.isVisible)
        assertEquals("otpDigits must be cleared on dismiss", "", viewModel.uiState.value.dailyOtpSheet.otpDigits)
    }

    // --- Fake Repository ---
    class FakeOtpTradingRepository : TradingRepository(
        api = createDummyApi(),
        wsClient = createDummyWsClient(),
        configStore = createDummyConfigStore()
    ) {
        val submitOtpCallCount = AtomicInteger(0)
        var lastSubmittedOtp: String? = null
        var otpResponse: ApiResult<DailyLoginResponse> = ApiResult.Success(
            DailyLoginResponse(brokerConnected = true, tradeDate = "2026-11-21")
        )

        val wsConnectCallCount = AtomicInteger(0)
        val optionChainCallCount = AtomicInteger(0)
        val healthCallCount = AtomicInteger(0)

        override suspend fun submitDailyOtp(otp: String): ApiResult<DailyLoginResponse> {
            submitOtpCallCount.incrementAndGet()
            lastSubmittedOtp = otp
            return otpResponse
        }

        override fun connectWebSocket() {
            wsConnectCallCount.incrementAndGet()
        }

        override suspend fun checkHealth(): ApiResult<HealthDto> {
            healthCallCount.incrementAndGet()
            return ApiResult.Success(
                HealthDto(
                    status = "ok",
                    brokerMode = "live",
                    brokerConnected = true,
                    wsFeedHealthy = true,
                    openPositionsCount = 0,
                    memoryRssMb = 50L,
                    uptimeSec = 100L
                )
            )
        }

        override suspend fun getAuthStatus(): ApiResult<AuthStatusResponse> = ApiResult.Success(
            AuthStatusResponse(status = "FEED_READY", brokerConnected = true, wsFeedHealthy = true,
                wsFeedStatus = "CONNECTED", brokerMode = "live", tradingEnabled = true,
                sessionTokenPresent = true, sessionValid = true, sessionValidity = "VALID",
                wsTransportConnected = true, wsAuthenticated = true, marketDataReady = true)
        )

        override suspend fun getOptionChain(symbol: String, expiry: String, strikes: Int): ApiResult<OptionChainResponse> {
            optionChainCallCount.incrementAndGet()
            return ApiResult.Success(
                OptionChainResponse(
                    symbol = symbol,
                    expiry = "2026-11-26",
                    strikes = listOf(
                        OptionStrikeDto(
                            strikePrice = 24500.0,
                            ceToken = "26000",
                            ceSymbol = "NIFTY26NOV24500CE",
                            ceLtpPaise = 10000L
                        )
                    )
                )
            )
        }

        override suspend fun getLoginUrl(): ApiResult<String> {
            return ApiResult.Success("https://auth.flattrade.in/?app_key=test_key")
        }

        companion object {
            private fun createDummyConfigStore(): ConfigStore = object : ConfigStore {
                override fun getServerBaseUrl(): String = "http://localhost:3000"
                override fun setServerBaseUrl(url: String) {}
                override fun getJwtToken(): String? = "jwt-test"
                override fun setJwtToken(token: String?) {}
                override fun getDeviceId(): String = "device-test"
                override fun clearAuth() {}
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

            private fun createDummyApi(): TradingApi = object : TradingApi {
                override suspend fun provisionDevice(request: ProvisionDeviceRequest) = throw NotImplementedError()
                override suspend fun getHealth() = throw NotImplementedError()
                override suspend fun getAuthStatus() = throw NotImplementedError()
                override suspend fun getLoginUrl() = throw NotImplementedError()
                override suspend fun dailyLogin(request: DailyLoginRequest) = throw NotImplementedError()
                override suspend fun getOptionChain(symbol: String, expiry: String, strikes: Int) = throw NotImplementedError()
                override suspend fun placeOneClickOrder(request: OneClickOrderRequest) = throw NotImplementedError()
                override suspend fun patchLadder(token: String, request: PatchLadderRequest) = throw NotImplementedError()
                override suspend fun exitPosition(request: ExitPositionRequest) = throw NotImplementedError()
                override suspend fun exitAll(request: ExitAllRequest) = throw NotImplementedError()
                override suspend fun getTodayTrades() = throw NotImplementedError()
            }

            private fun createDummyWsClient(): TradingWsClient =
                TradingWsClient(okhttp3.OkHttpClient(), createDummyConfigStore())
        }
    }
}
