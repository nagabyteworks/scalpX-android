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
import kotlinx.coroutines.delay
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
                marketDataTimestamp = System.currentTimeMillis(),
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
                marketDataTimestamp = System.currentTimeMillis(),
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
                marketDataTimestamp = System.currentTimeMillis(),
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
                marketDataTimestamp = System.currentTimeMillis(),
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

    @Test
    fun `OneClickBuy is blocked when trading is disabled`() = runTest(testDispatcher) {
        fakeRepo.healthTradingEnabled = false
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })
        testScheduler.advanceUntilIdle()

        viewModel.handleIntent(
            ScalperIntent.OneClickBuy(
                token = "26000",
                symbol = "NIFTY26NOV24500CE",
                ltpPaise = 10000L,
                lotSize = 25
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals("Order must NOT be dispatched when tradingEnabled=false", 0, fakeRepo.oneClickOrderCallCount.get())
    }

    @Test
    fun `OneClickBuy is blocked when broker account state is uncertain`() = runTest(testDispatcher) {
        fakeRepo.healthAccountStateUncertain = true
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })
        testScheduler.advanceUntilIdle()

        viewModel.handleIntent(
            ScalperIntent.OneClickBuy(
                token = "26000",
                symbol = "NIFTY26NOV24500CE",
                ltpPaise = 10000L,
                lotSize = 25
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals("Order must NOT be dispatched when accountStateUncertain=true", 0, fakeRepo.oneClickOrderCallCount.get())
    }

    @Test
    fun `OneClickBuy is blocked when broker is disconnected`() = runTest(testDispatcher) {
        fakeRepo.healthBrokerConnected = false
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })
        testScheduler.advanceUntilIdle()

        viewModel.handleIntent(
            ScalperIntent.OneClickBuy(
                token = "26000",
                symbol = "NIFTY26NOV24500CE",
                ltpPaise = 10000L,
                lotSize = 25
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals("Order must NOT be dispatched when broker is disconnected", 0, fakeRepo.oneClickOrderCallCount.get())
    }

    @Test
    fun `incoming market snapshots immediately update spot and option prices`() = runTest(testDispatcher) {
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })
        testScheduler.advanceUntilIdle()

        val updateTime = System.currentTimeMillis()
        fakeRepo.emitSnapshot(fakeRepo.marketSnapshot(
            spotLtpPaise = 8_201_000L,
            callLtpPaise = 12_500L,
            putLtpPaise = 11_250L,
            timestamp = updateTime
        ))
        testScheduler.advanceUntilIdle()

        assertEquals(8_201_000L, viewModel.uiState.value.spotLtpPaise)
        assertEquals("READY", viewModel.uiState.value.spotStatus)
        assertEquals(12_500L, viewModel.uiState.value.strikes.single().ceLtpPaise)
        assertEquals(11_250L, viewModel.uiState.value.strikes.single().peLtpPaise)
        assertEquals(updateTime, viewModel.uiState.value.strikes.single().ceTimestamp)
    }

    @Test
    fun `startup retries waiting spot until ready and then requests the option chain`() = runTest(testDispatcher) {
        fakeRepo.waitingSpotResponses = 1

        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })
        runCurrent()
        assertEquals(1, fakeRepo.indexSpotCallCount)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, fakeRepo.indexSpotCallCount)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, fakeRepo.indexSpotCallCount)
        assertEquals(1, fakeRepo.optionChainCallCount)
        assertEquals("READY", viewModel.uiState.value.spotStatus)
    }

    @Test
    fun `startup health-check retries temporary failures before opening the websocket`() = runTest(testDispatcher) {
        fakeRepo.healthFailuresRemaining = 2
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })

        runCurrent()
        assertEquals(1, fakeRepo.healthCallCount)
        assertEquals(0, fakeRepo.connectWebSocketCallCount)
        assertEquals("RETRYING", viewModel.uiState.value.backendReachability)
        assertTrue(viewModel.uiState.value.lastServerMessage.contains("backend unreachable"))

        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, fakeRepo.healthCallCount)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, fakeRepo.healthCallCount)
        assertEquals(0, fakeRepo.connectWebSocketCallCount)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(2, fakeRepo.healthCallCount)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, fakeRepo.healthCallCount)
        assertEquals(1, fakeRepo.connectWebSocketCallCount)
        assertEquals("ONLINE", viewModel.uiState.value.backendReachability)
        assertEquals(1, fakeRepo.indexSpotCallCount)
    }

    @Test
    fun `startup does not retry permanent health errors or connect websocket`() = runTest(testDispatcher) {
        fakeRepo.healthFailuresRemaining = 1
        fakeRepo.healthFailureStatusCode = 404
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })

        runCurrent()

        assertEquals(1, fakeRepo.healthCallCount)
        assertEquals(0, fakeRepo.connectWebSocketCallCount)
        assertEquals("OFFLINE", viewModel.uiState.value.backendReachability)
        assertEquals(0, fakeRepo.indexSpotCallCount)
    }

    @Test
    fun `spot retry uses capped exponential backoff and manual retry resets it`() = runTest(testDispatcher) {
        fakeRepo.waitingSpotResponses = 20
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })

        runCurrent()
        assertEquals(1, fakeRepo.indexSpotCallCount)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, fakeRepo.indexSpotCallCount)
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(2, fakeRepo.indexSpotCallCount)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, fakeRepo.indexSpotCallCount)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(4, fakeRepo.indexSpotCallCount)
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(5, fakeRepo.indexSpotCallCount)
        advanceTimeBy(16_000)
        runCurrent()
        assertEquals(6, fakeRepo.indexSpotCallCount)
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(6, fakeRepo.indexSpotCallCount)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(7, fakeRepo.indexSpotCallCount)

        fakeRepo.waitingSpotResponses = 1
        viewModel.refreshSensexSpot()
        runCurrent()
        assertEquals(8, fakeRepo.indexSpotCallCount)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(8, fakeRepo.indexSpotCallCount)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(9, fakeRepo.indexSpotCallCount)
        assertEquals("READY", viewModel.uiState.value.spotStatus)
    }

    @Test
    fun `manual spot retry cancels an in-flight request before starting another`() = runTest(testDispatcher) {
        fakeRepo.spotRequestDelayMs = 10_000
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })
        runCurrent()
        assertEquals(1, fakeRepo.activeSpotRequests)

        viewModel.refreshSensexSpot()
        runCurrent()

        assertEquals(2, fakeRepo.indexSpotCallCount)
        assertEquals(1, fakeRepo.activeSpotRequests)
        assertEquals(1, fakeRepo.maxConcurrentSpotRequests)
    }

    @Test
    fun `Sell action dispatches a direct short order with a lower first target`() = runTest(testDispatcher) {
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })
        testScheduler.advanceUntilIdle()

        viewModel.handleIntent(
            ScalperIntent.OneClickSell(
                token = "26000",
                symbol = "SENSEX26NOV2682000CE",
                ltpPaise = 10_000L,
                marketDataTimestamp = System.currentTimeMillis(),
                lotSize = 20
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals("SELL", fakeRepo.lastSubmittedSide)
        assertEquals(9_000L, fakeRepo.lastSubmittedFirstMilestonePaise)
        assertEquals(1, fakeRepo.oneClickOrderCallCount.get())
    }

    @Test
    fun `invalid broker session does not open an app authentication sheet`() = runTest(testDispatcher) {
        fakeRepo.healthBrokerConnected = false
        fakeRepo.healthSessionValidity = "INVALID"
        val viewModel = ScalperViewModel(repository = fakeRepo, clock = { testCurrentTime })
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sessionValid)
        assertEquals("INVALID", viewModel.uiState.value.sessionValidity)
        assertFalse(viewModel.uiState.value.dailyOtpSheet.isVisible)
    }

    private class FakeTradingRepository : TradingRepository(
        api = createDummyApi(),
        wsClient = createDummyWsClient(),
        configStore = createDummyConfigStore()
    ) {
        val oneClickOrderCallCount = AtomicInteger(0)
        var indexSpotCallCount = 0
        var optionChainCallCount = 0
        var waitingSpotResponses = 0
        var spotRequestDelayMs = 0L
        var activeSpotRequests = 0
        var maxConcurrentSpotRequests = 0
        var healthCallCount = 0
        var healthFailuresRemaining = 0
        var healthFailureStatusCode: Int? = null
        var connectWebSocketCallCount = 0
        var lastSubmittedFirstMilestonePaise: Long? = null
        var lastSubmittedSide: String? = null
        var lastPatchedToken: String? = null
        var lastPatchedNextMilestonePaise: Long? = null

        var healthTradingEnabled = true
        var healthBrokerConnected = true
        var healthSessionValidity = "VALID"
        var healthAccountStateUncertain = false
        var healthWsFeedStatus = "FEED_CONNECTED"

        private val testSnapshotFlow = MutableStateFlow<TradingSnapshotMessage?>(null)
        override val snapshotStream = testSnapshotFlow

        private val testAlertsFlow = MutableSharedFlow<PriorityAlert>(extraBufferCapacity = 16)
        override val priorityAlerts = testAlertsFlow

        private val testConnState = MutableStateFlow<WsConnectionState>(WsConnectionState.Connected(12L))
        override val connectionState = testConnState

        private val testPing = MutableStateFlow(12L)
        override val pingMs = testPing

        override fun connectWebSocket() {
            connectWebSocketCallCount++
            testSnapshotFlow.value = marketSnapshot().copy(marketData = null)
        }

        override fun disconnectWebSocket() {}

        fun marketSnapshot(
            spotLtpPaise: Long = 8_200_000L,
            callLtpPaise: Long = 10_000L,
            putLtpPaise: Long = 9_000L,
            timestamp: Long = System.currentTimeMillis()
        ) = TradingSnapshotMessage(
            timestamp = timestamp,
            openPositionsCount = 0,
            marketData = MarketDataSnapshotDto(
                indexSymbol = "SENSEX",
                spotToken = "901",
                spotLtpPaise = spotLtpPaise,
                spotTimestamp = timestamp,
                selectedExpiry = "2026-11-26",
                atmStrike = 82_000.0,
                ceSymbol = "SENSEX26NOV2682000CE",
                ceToken = "26000",
                ceLotSize = 20,
                ceLtpPaise = callLtpPaise,
                ceTimestamp = timestamp,
                peSymbol = "SENSEX26NOV2682000PE",
                peToken = "26001",
                peLotSize = 20,
                peLtpPaise = putLtpPaise,
                peTimestamp = timestamp,
                marketDataTimestamp = timestamp,
                feedStatus = "CONNECTED",
                strikes = listOf(
                    OptionStrikeDto(
                        strikePrice = 82_000.0,
                        ceToken = "26000",
                        ceSymbol = "SENSEX26NOV2682000CE",
                        ceLotSize = 20,
                        ceLtpPaise = callLtpPaise,
                        ceTimestamp = timestamp,
                        peToken = "26001",
                        peSymbol = "SENSEX26NOV2682000PE",
                        peLotSize = 20,
                        peLtpPaise = putLtpPaise,
                        peTimestamp = timestamp
                    )
                )
            )
        )

        suspend fun emitSnapshot(snapshot: TradingSnapshotMessage) {
            testSnapshotFlow.value = snapshot
        }

        suspend fun emitPriorityAlert(alert: PriorityAlert) {
            testAlertsFlow.emit(alert)
        }

        override suspend fun checkHealth(): ApiResult<HealthDto> {
            healthCallCount++
            if (healthFailuresRemaining > 0) {
                healthFailuresRemaining--
                return ApiResult.Error("Connection refused", statusCode = healthFailureStatusCode)
            }
            return ApiResult.Success(
                HealthDto(
                    status = "ok",
                    environment = "LIVE",
                    brokerConnected = healthBrokerConnected,
                    wsFeedHealthy = true,
                    tradingEnabled = healthTradingEnabled,
                    wsFeedStatus = healthWsFeedStatus,
                    accountStateUncertain = healthAccountStateUncertain,
                    openPositionsCount = 0,
                    memoryRssMb = 45L,
                    uptimeSec = 300L
                )
            )
        }

        override suspend fun getAuthStatus(): ApiResult<AuthStatusResponse> = ApiResult.Success(
            AuthStatusResponse(
                status = if (healthBrokerConnected) "FEED_READY" else "WEBSOCKET_DISCONNECTED",
                brokerConnected = healthBrokerConnected,
                wsFeedHealthy = healthBrokerConnected,
                wsFeedStatus = healthWsFeedStatus,
                environment = "LIVE",
                tradingEnabled = healthTradingEnabled,
                sessionTokenPresent = true,
                sessionValid = healthBrokerConnected,
                sessionValidity = if (healthBrokerConnected) "VALID" else healthSessionValidity,
                wsTransportConnected = healthBrokerConnected,
                wsAuthenticated = healthBrokerConnected,
                marketDataReady = healthBrokerConnected,
                accountStateUncertain = healthAccountStateUncertain
            )
        )

        override suspend fun getOptionChain(symbol: String, expiry: String, strikes: Int): ApiResult<OptionChainResponse> {
            optionChainCallCount++
            return ApiResult.Success(
                OptionChainResponse(
                    symbol = symbol,
                    expiry = "2026-11-26",
                    strikes = emptyList()
                )
            )
        }

        override suspend fun getIndexSpot(symbol: String): ApiResult<SensexSpotDto> {
            indexSpotCallCount++
            activeSpotRequests++
            maxConcurrentSpotRequests = maxOf(maxConcurrentSpotRequests, activeSpotRequests)
            try {
                if (spotRequestDelayMs > 0) delay(spotRequestDelayMs)
                val waiting = waitingSpotResponses > 0
                if (waiting) waitingSpotResponses--
                return ApiResult.Success(
                    SensexSpotDto(
                        index = symbol,
                        exchange = if (symbol == "SENSEX") "BSE" else "NSE",
                        status = if (waiting) "WAITING_FOR_TICK" else "READY",
                        spotLtpPaise = if (waiting) null else 8_200_000L,
                        timestamp = if (waiting) null else System.currentTimeMillis(),
                        ageMs = if (waiting) null else 0L,
                        marketDataReady = !waiting
                    )
                )
            } finally {
                activeSpotRequests--
            }
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
            initialSlPoints: Double?,
            side: String,
            product: String
        ): ApiResult<LadderPositionDto> {
            oneClickOrderCallCount.incrementAndGet()
            lastSubmittedFirstMilestonePaise = firstMilestonePaise
            lastSubmittedSide = side
            return ApiResult.Success(
                LadderPositionDto(
                    token = token,
                    symbol = symbol,
                    side = side,
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
                override suspend fun getAppConfig() = throw NotImplementedError()
                override suspend fun getAuthStatus() = throw NotImplementedError()
                override suspend fun getSensexSpot() = throw NotImplementedError()
                override suspend fun getIndexSpot(symbol: String) = throw NotImplementedError()
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
