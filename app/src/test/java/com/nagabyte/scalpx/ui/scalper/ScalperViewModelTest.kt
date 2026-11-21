package com.nagabyte.scalpx.ui.scalper

import com.nagabyte.scalpx.data.local.ConfigStore
import com.nagabyte.scalpx.data.remote.TradingApi
import com.nagabyte.scalpx.data.remote.TradingWsClient
import com.nagabyte.scalpx.data.remote.WsConnectionState
import com.nagabyte.scalpx.data.remote.dto.*
import com.nagabyte.scalpx.data.repository.ApiResult
import com.nagabyte.scalpx.data.repository.TradingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ScalperViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeTradingRepository
    private var testCurrentTime = 1000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testCurrentTime = 1000L
        fakeRepo = FakeTradingRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tapping OneClickBuy twice within 300 ms dispatches ONLY ONE repository call`() = runTest(testDispatcher) {
        val viewModel = ScalperViewModel(
            repository = fakeRepo,
            clock = { testCurrentTime }
        )
        testScheduler.advanceUntilIdle()

        // 1. First tap at t = 1000 ms
        viewModel.handleIntent(
            ScalperIntent.OneClickBuy(
                token = "26000",
                symbol = "NIFTY26NOV24500CE",
                ltpPaise = 10000L,
                lotSize = 25
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals("First tap must dispatch repository order call", 1, fakeRepo.oneClickOrderCallCount.get())

        // 2. Second tap at t = 1300 ms (within 500 ms cooldown window)
        testCurrentTime = 1300L
        viewModel.handleIntent(
            ScalperIntent.OneClickBuy(
                token = "26000",
                symbol = "NIFTY26NOV24500CE",
                ltpPaise = 10000L,
                lotSize = 25
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals("Second tap within 300ms must be dropped by cooldown guard", 1, fakeRepo.oneClickOrderCallCount.get())

        // 3. Third tap after cooldown expires at t = 1900 ms (> 500 ms)
        testCurrentTime = 1900L
        viewModel.handleIntent(
            ScalperIntent.OneClickBuy(
                token = "26000",
                symbol = "NIFTY26NOV24500CE",
                ltpPaise = 10000L,
                lotSize = 25
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals("Tap after cooldown must dispatch second order call", 2, fakeRepo.oneClickOrderCallCount.get())
    }

    @Test
    fun `SL_RATCHETED and ORDER_FILLED emit haptic feedback effects`() = runTest(testDispatcher) {
        val viewModel = ScalperViewModel(
            repository = fakeRepo,
            clock = { testCurrentTime }
        )
        testScheduler.advanceUntilIdle()

        val effects = mutableListOf<ScalperUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEffect.collect { effects.add(it) }
        }

        // 1. Emit ORDER_FILLED event
        fakeRepo.emitPriorityAlert(
            PriorityAlert(
                type = "ORDER_FILLED",
                message = "Order filled for 26000: 50 qty @ ₹100.00",
                token = "26000"
            )
        )
        testScheduler.advanceUntilIdle()

        val haptic1 = effects.filterIsInstance<ScalperUiEffect.TriggerHaptic>().firstOrNull()
        assertNotNull("ORDER_FILLED must emit TriggerHaptic", haptic1)
        assertFalse("ORDER_FILLED haptic is standard priority", haptic1!!.isImportant)

        effects.clear()

        // 2. Emit SL_RATCHETED event
        fakeRepo.emitPriorityAlert(
            PriorityAlert(
                type = "SL_RATCHETED",
                message = "SL ratcheted for 26000: Trg ₹115.00, Lmt ₹113.50",
                token = "26000"
            )
        )
        testScheduler.advanceUntilIdle()

        val haptic2 = effects.filterIsInstance<ScalperUiEffect.TriggerHaptic>().firstOrNull()
        assertNotNull("SL_RATCHETED must emit TriggerHaptic", haptic2)
        assertTrue("SL_RATCHETED haptic must be marked as important", haptic2!!.isImportant)
    }

    @Test
    fun `MANUAL_INTERVENTION_REQUIRED sets criticalAlert and does not auto-dismiss on subsequent 150 ms snapshots`() = runTest(testDispatcher) {
        val viewModel = ScalperViewModel(
            repository = fakeRepo,
            clock = { testCurrentTime }
        )
        testScheduler.advanceUntilIdle()

        // 1. Initially no critical alert
        assertNull(viewModel.uiState.value.criticalAlert)

        // 2. Emit MANUAL_INTERVENTION_REQUIRED
        fakeRepo.emitPriorityAlert(
            PriorityAlert(
                type = "MANUAL_INTERVENTION_REQUIRED",
                message = "CRITICAL: Manual intervention required for 26000! Broker timeout",
                token = "26000"
            )
        )
        testScheduler.advanceUntilIdle()

        val alert = viewModel.uiState.value.criticalAlert
        assertNotNull("MANUAL_INTERVENTION_REQUIRED must set criticalAlert in state", alert)
        assertEquals("MANUAL_INTERVENTION_REQUIRED", alert?.type)
        assertEquals("26000", alert?.token)

        // 3. Emit a new 150 ms TradingSnapshot
        val newSnapshot = TradingSnapshotMessage(
            type = "SNAPSHOT",
            timestamp = 1732174200150L,
            openPositionsCount = 1,
            positions = listOf(
                PositionSnapshotItemDto(
                    token = "26000",
                    symbol = "NIFTY26NOV24500CE",
                    quantity = 50,
                    entryPricePaise = 10000L,
                    currentLtpPaise = 11150L,
                    unrealizedPnlPaise = 57500L,
                    nextMilestonePaise = 11500L,
                    activeSlTriggerPaise = 11000L,
                    activeSlLimitPaise = 10850L,
                    isPaused = false,
                    status = "OPEN"
                )
            )
        )
        fakeRepo.emitSnapshot(newSnapshot)
        testScheduler.advanceUntilIdle()

        // Verify positions updated
        assertEquals(1, viewModel.uiState.value.positions.size)
        assertEquals(57500L, viewModel.uiState.value.totalUnrealizedPnlPaise)

        // Verify critical alert has NOT auto-dismissed
        val persistentAlert = viewModel.uiState.value.criticalAlert
        assertNotNull("criticalAlert must PERSIST across subsequent 150 ms snapshot frames", persistentAlert)
        assertEquals("MANUAL_INTERVENTION_REQUIRED", persistentAlert?.type)

        // 4. Dismiss alert explicitly
        viewModel.handleIntent(ScalperIntent.DismissAlert)
        testScheduler.advanceUntilIdle()
        assertNull("criticalAlert is cleared only upon explicit user dismissal", viewModel.uiState.value.criticalAlert)
    }

    @Test
    fun `OneClickBuy aligns first milestone price to NSE 5-paise tick`() = runTest(testDispatcher) {
        val viewModel = ScalperViewModel(
            repository = fakeRepo,
            clock = { testCurrentTime }
        )
        testScheduler.advanceUntilIdle()

        // Draft first target is 10.0 pts (1000 paise). LTP = 10001 paise (not tick-aligned).
        // Target = 10001 + 1000 = 11001 paise, floored to 5-paise tick = 11000 paise.
        viewModel.handleIntent(
            ScalperIntent.OneClickBuy(
                token = "26000",
                symbol = "NIFTY26NOV24500CE",
                ltpPaise = 10001L,
                lotSize = 25
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals(11000L, fakeRepo.lastSubmittedFirstMilestonePaise)
    }

    @Test
    fun `AdjustMilestoneByDelta updates local milestone by delta and patches ladder`() = runTest(testDispatcher) {
        val viewModel = ScalperViewModel(
            repository = fakeRepo,
            clock = { testCurrentTime }
        )
        testScheduler.advanceUntilIdle()

        // Seed snapshot with active position at milestone 11000 paise
        fakeRepo.emitSnapshot(
            TradingSnapshotMessage(
                timestamp = 1732174200000L,
                openPositionsCount = 1,
                positions = listOf(
                    PositionSnapshotItemDto(
                        token = "26000",
                        symbol = "NIFTY26NOV24500CE",
                        quantity = 50,
                        entryPricePaise = 10000L,
                        currentLtpPaise = 10500L,
                        unrealizedPnlPaise = 25000L,
                        nextMilestonePaise = 11000L,
                        status = "OPEN"
                    )
                )
            )
        )
        testScheduler.advanceUntilIdle()

        // Quick +100 paise (+₹1.00) button
        viewModel.handleIntent(ScalperIntent.AdjustMilestoneByDelta(token = "26000", deltaPaise = 100L))
        testScheduler.advanceUntilIdle()

        assertEquals("26000", fakeRepo.lastPatchedToken)
        assertEquals(11100L, fakeRepo.lastPatchedNextMilestonePaise)
    }

    private class FakeTradingRepository : TradingRepository(
        api = createDummyApi(),
        wsClient = createDummyWsClient(),
        configStore = createDummyConfigStore()
    ) {
        val oneClickOrderCallCount = AtomicInteger(0)
        var lastSubmittedFirstMilestonePaise: Long? = null
        var lastPatchedToken: String? = null
        var lastPatchedNextMilestonePaise: Long? = null

        private val testSnapshotFlow = MutableStateFlow<TradingSnapshotMessage?>(null)
        override val snapshotStream = testSnapshotFlow

        private val testAlertsFlow = MutableSharedFlow<PriorityAlert>(extraBufferCapacity = 16)
        override val priorityAlerts = testAlertsFlow

        private val testConnState = MutableStateFlow<WsConnectionState>(WsConnectionState.Connected(12L))
        override val connectionState = testConnState

        private val testPing = MutableStateFlow(12L)
        override val pingMs = testPing

        suspend fun emitSnapshot(snapshot: TradingSnapshotMessage) {
            testSnapshotFlow.value = snapshot
        }

        suspend fun emitPriorityAlert(alert: PriorityAlert) {
            testAlertsFlow.emit(alert)
        }

        override suspend fun checkHealth(): ApiResult<HealthDto> {
            return ApiResult.Success(
                HealthDto(
                    status = "ok",
                    brokerMode = "paper",
                    brokerConnected = true,
                    wsFeedHealthy = true,
                    openPositionsCount = 0,
                    memoryRssMb = 45L,
                    uptimeSec = 300L
                )
            )
        }

        override suspend fun getOptionChain(symbol: String, expiry: String, strikes: Int): ApiResult<OptionChainResponse> {
            return ApiResult.Success(
                OptionChainResponse(
                    symbol = symbol,
                    expiry = "26-NOV-2026",
                    strikes = emptyList()
                )
            )
        }

        override suspend fun executeOneClickOrder(
            token: String,
            symbol: String,
            exchange: String,
            lots: Int,
            lotSize: Int,
            stepSizePaise: Long,
            breakoutBufferPaise: Long,
            slippageBufferPaise: Long,
            firstMilestonePaise: Long?,
            initialSlPoints: Double?
        ): ApiResult<LadderPositionDto> {
            oneClickOrderCallCount.incrementAndGet()
            lastSubmittedFirstMilestonePaise = firstMilestonePaise
            return ApiResult.Success(
                LadderPositionDto(
                    token = token,
                    symbol = symbol,
                    quantity = lots * lotSize,
                    entryPricePaise = 10000L,
                    nextMilestonePaise = firstMilestonePaise ?: 11000L,
                    stepSizePaise = stepSizePaise,
                    breakoutBufferPaise = breakoutBufferPaise,
                    slippageBufferPaise = slippageBufferPaise,
                    status = "OPEN"
                )
            )
        }

        override suspend fun patchLadder(
            token: String,
            nextMilestonePaise: Long?,
            stepSizePaise: Long?,
            breakoutBufferPaise: Long?,
            slippageBufferPaise: Long?,
            paused: Boolean?
        ): ApiResult<LadderPositionDto> {
            lastPatchedToken = token
            lastPatchedNextMilestonePaise = nextMilestonePaise
            return ApiResult.Success(
                LadderPositionDto(
                    token = token,
                    symbol = "TEST",
                    quantity = 25,
                    entryPricePaise = 10000L,
                    nextMilestonePaise = nextMilestonePaise ?: 11000L,
                    stepSizePaise = 500L,
                    breakoutBufferPaise = 100L,
                    slippageBufferPaise = 150L,
                    status = "OPEN"
                )
            )
        }

        override suspend fun exitPosition(token: String): ApiResult<ExitPositionResponse> {
            return ApiResult.Success(
                ExitPositionResponse(
                    success = true,
                    token = token,
                    commandId = "cmd-test",
                    orderId = "ord-exit",
                    status = "FILLED"
                )
            )
        }

        override suspend fun panicExitAll(): ApiResult<ExitAllResponse> {
            return ApiResult.Success(ExitAllResponse(success = true, message = "All exited"))
        }

        override suspend fun getLoginUrl(): ApiResult<String> {
            return ApiResult.Success("https://auth.flattrade.in/?app_key=test")
        }

        companion object {
            private fun createDummyConfigStore(): ConfigStore = object : ConfigStore {
                override fun getServerBaseUrl(): String = "http://localhost:3000"
                override fun setServerBaseUrl(url: String) {}
                override fun getJwtToken(): String? = "jwt"
                override fun setJwtToken(token: String?) {}
                override fun getDeviceId(): String = "device-1"
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
                override suspend fun getHealth() = throw NotImplementedError()
                override suspend fun getLoginUrl() = throw NotImplementedError()
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
