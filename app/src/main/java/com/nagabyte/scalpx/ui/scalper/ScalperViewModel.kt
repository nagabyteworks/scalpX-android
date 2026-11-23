package com.nagabyte.scalpx.ui.scalper

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.data.remote.ServerConfig
import com.nagabyte.scalpx.data.remote.WsConnectionState
import com.nagabyte.scalpx.data.remote.dto.MarketDataSnapshotDto
import com.nagabyte.scalpx.data.repository.ApiResult
import com.nagabyte.scalpx.data.repository.TradingRepository
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Server-authoritative MVI ViewModel for the scalpX 1-click execution cockpit.
 * Invariant: Strict 500 ms tap cooldown + inFlight command deduplication.
 * Critical alerts persist across 150 ms WebSocket snapshot ticks until user dismisses.
 */
class ScalperViewModel(
    private val repository: TradingRepository,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScalperUiState())
    val uiState: StateFlow<ScalperUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<ScalperUiEffect>(extraBufferCapacity = 32)
    val uiEffect: SharedFlow<ScalperUiEffect> = _uiEffect.asSharedFlow()

    private val lastTapTimestamps = ConcurrentHashMap<String, Long>()
    private var backendConnectivityJob: Job? = null
    private var optionChainRefreshJob: Job? = null
    private var spotRefreshJob: Job? = null
    private var spotRetryDelayMs = SPOT_INITIAL_RETRY_DELAY_MS
    private var lastOptionChainAttemptAt = 0L

    init {
        observeRepositoryStreams()
        loadPresetsFromConfig()
        checkBackendConnectivity()
    }

    private fun loadPresetsFromConfig() {
        val config = repository.configStore
        _uiState.update { current ->
            current.copy(
                selectedLots = config.getDefaultLots(),
                ladderDraft = LadderDraftUi(
                    firstTargetPts = config.getFirstTargetPts(),
                    stepPts = config.getStepPts(),
                    breakoutBufferPts = config.getBreakoutBufferPts(),
                    slippageBufferPts = config.getSlippageBufferPts(),
                    initialSlPts = 10.0
                )
            )
        }
    }

    private fun observeRepositoryStreams() {
        // 1. Observe 150 ms coalesced TradingSnapshots
        viewModelScope.launch {
            repository.snapshotStream.filterNotNull().collect { snapshot ->
                val activeList = snapshot.positions.map { pos ->
                    ActivePositionUi(
                        token = pos.token,
                        symbol = pos.symbol,
                        side = pos.side,
                        quantity = pos.quantity,
                        entryPricePaise = pos.entryPricePaise,
                        currentLtpPaise = pos.currentLtpPaise,
                        unrealizedPnlPaise = pos.unrealizedPnlPaise,
                        exchangeSlTriggerPaise = pos.activeSlTriggerPaise,
                        exchangeSlLimitPaise = pos.activeSlLimitPaise,
                        localNextMilestonePaise = pos.nextMilestonePaise,
                        stepSizePaise = 500L,
                        ratchetPaused = pos.isPaused,
                        status = pos.status
                    )
                }.toPersistentList()

                val totalUnrealized = activeList.sumOf { it.unrealizedPnlPaise }

                _uiState.update { current ->
                    // Crucial: keep criticalAlert intact across high-frequency 150 ms updates
                    current.copy(
                        positions = activeList,
                        totalUnrealizedPnlPaise = totalUnrealized
                    )
                }
                snapshot.marketData?.let(::applyMarketDataSnapshot)
            }
        }

        // 2. Observe WebSocket feed connection state & latency
        viewModelScope.launch {
            repository.connectionState.collect { connState ->
                val wsState = when (connState) {
                    is WsConnectionState.Connected -> WsState.CONNECTED
                    is WsConnectionState.Connecting -> WsState.CONNECTING
                    is WsConnectionState.Disconnected -> WsState.DISCONNECTED
                    is WsConnectionState.Failed -> WsState.DISCONNECTED
                }
                _uiState.update { it.copy(serverWsState = wsState) }
            }
        }

        viewModelScope.launch {
            repository.snapshotAgeMs.collect { age ->
                _uiState.update { it.copy(snapshotAgeMs = age, pingMs = age) }
            }
        }

        // 3. Observe high-priority events (ORDER_FILLED, SL_RATCHETED, etc.)
        viewModelScope.launch {
            repository.priorityAlerts.collect { alert ->
                when (alert.type) {
                    "ORDER_FILLED" -> {
                        _uiEffect.emit(ScalperUiEffect.TriggerHaptic())
                        _uiEffect.emit(ScalperUiEffect.PlaySound("ORDER_FILLED"))
                        _uiEffect.emit(ScalperUiEffect.ShowToast(alert.message))
                    }
                    "SL_RATCHETED" -> {
                        _uiEffect.emit(ScalperUiEffect.TriggerHaptic(isImportant = true))
                        _uiEffect.emit(ScalperUiEffect.PlaySound("SL_RATCHETED"))
                        _uiEffect.emit(ScalperUiEffect.ShowToast(alert.message))
                    }
                    "MANUAL_INTERVENTION_REQUIRED" -> {
                        _uiEffect.emit(ScalperUiEffect.PlaySound("CRITICAL_ALERT"))
                        _uiState.update { current ->
                            current.copy(
                                criticalAlert = CriticalAlertUi(
                                    type = alert.type,
                                    message = alert.message,
                                    token = alert.token,
                                    timestamp = alert.timestamp
                                )
                            )
                        }
                    }
                    "FEED_STALE" -> {
                        _uiState.update { current ->
                            current.copy(
                                criticalAlert = CriticalAlertUi(
                                    type = alert.type,
                                    message = alert.message,
                                    token = alert.token,
                                    timestamp = alert.timestamp
                                )
                            )
                        }
                    }
                    "ORDER_REJECTED" -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast(alert.message))
                    }
                }
            }
        }
    }

    private fun applyMarketDataSnapshot(market: MarketDataSnapshotDto) {
        _uiState.update { current ->
            if (market.indexSymbol != current.selectedIndex ||
                (current.selectedExpiry.isNotBlank() && market.selectedExpiry != current.selectedExpiry)) {
                return@update current
            }

            val updatedStrikes = market.strikes.map { strike ->
                OptionStrikeUi(
                    strikePrice = strike.strikePrice,
                    ceToken = strike.ceToken,
                    ceSymbol = strike.ceSymbol,
                    ceLotSize = strike.ceLotSize,
                    ceLtpPaise = strike.ceLtpPaise,
                    ceTimestamp = strike.ceTimestamp,
                    peToken = strike.peToken,
                    peSymbol = strike.peSymbol,
                    peLotSize = strike.peLotSize,
                    peLtpPaise = strike.peLtpPaise,
                    peTimestamp = strike.peTimestamp
                )
            }.toPersistentList()
            val selectedCall = current.selectedCallStrike?.let { selected ->
                updatedStrikes.find { it.strikePrice == selected }
            } ?: updatedStrikes.find { it.strikePrice == market.atmStrike }
            val selectedPut = current.selectedPutStrike?.let { selected ->
                updatedStrikes.find { it.strikePrice == selected }
            } ?: updatedStrikes.find { it.strikePrice == market.atmStrike }
            val freshSpot = market.spotLtpPaise != null &&
                market.spotTimestamp != null &&
                System.currentTimeMillis() - market.spotTimestamp in 0L..3_000L &&
                market.feedStatus == "CONNECTED"
            val feedError = when (market.feedStatus) {
                "STALE" -> "Flattrade market-data ticks are stale."
                "DISCONNECTED", "RECONNECTING" -> "Flattrade market-data WebSocket is ${market.feedStatus.lowercase()}."
                "WAITING_FOR_TICK" -> "Waiting for fresh Flattrade spot and option ticks."
                else -> null
            }

            current.copy(
                selectedExchange = if (market.indexSymbol == "SENSEX") "BFO" else "NFO",
                selectedExpiry = market.selectedExpiry,
                strikes = if (updatedStrikes.isNotEmpty()) updatedStrikes else current.strikes,
                selectedStrike = current.selectedStrike ?: market.atmStrike,
                selectedCallStrike = selectedCall?.strikePrice ?: current.selectedCallStrike ?: market.atmStrike,
                selectedPutStrike = selectedPut?.strikePrice ?: current.selectedPutStrike ?: market.atmStrike,
                lotSize = selectedCall?.ceLotSize ?: selectedPut?.peLotSize ?: current.lotSize,
                spotLtpPaise = if (freshSpot) market.spotLtpPaise!! else 0L,
                spotStatus = if (freshSpot) "READY" else market.feedStatus,
                marketDataTimestamp = market.marketDataTimestamp,
                feedConnectionStatus = market.feedStatus,
                marketDataError = feedError
            )
        }
    }

    fun handleIntent(intent: ScalperIntent) {
        when (intent) {
            is ScalperIntent.SelectIndex -> selectIndex(intent.index)
            is ScalperIntent.SelectExpiry -> {
                _uiState.update {
                    it.copy(
                        selectedExpiry = intent.expiry,
                        selectedStrike = null,
                        selectedCallStrike = null,
                        selectedPutStrike = null
                    )
                }
                refreshOptionChain()
            }
            is ScalperIntent.SelectStrike -> _uiState.update { it.copy(selectedStrike = intent.strike, selectedCallStrike = intent.strike, selectedPutStrike = intent.strike) }
            is ScalperIntent.SelectCallStrike -> _uiState.update { it.copy(selectedCallStrike = intent.strike) }
            is ScalperIntent.SelectPutStrike -> _uiState.update { it.copy(selectedPutStrike = intent.strike) }
            is ScalperIntent.StepStrike -> stepStrike(intent.isCall, intent.deltaSteps)

            is ScalperIntent.SelectLots -> _uiState.update { it.copy(selectedLots = intent.lots) }
            is ScalperIntent.StepLots -> stepLots(intent.delta)
            is ScalperIntent.SelectProduct -> _uiState.update { it.copy(product = intent.product) }

            is ScalperIntent.ToggleZone2Collapse -> _uiState.update { it.copy(isZone2Collapsed = intent.collapsed) }
            is ScalperIntent.SelectBookTab -> _uiState.update { it.copy(activeTab = intent.tab) }
            is ScalperIntent.ToggleTableView -> _uiState.update { it.copy(isTableView = intent.isTable) }
            is ScalperIntent.ToggleTheme -> _uiState.update { it.copy(isDarkTheme = intent.isDark) }
            is ScalperIntent.OpenRatchetConfig -> _uiState.update { it.copy(isRatchetConfigOpen = intent.open) }
            is ScalperIntent.UpdateLadderDraft -> _uiState.update { it.copy(ladderDraft = intent.draft) }

            is ScalperIntent.OneClickBuy -> handleOneClickBuy(intent)
            is ScalperIntent.OneClickSell -> handleOneClickSell(intent)
            is ScalperIntent.AdjustMilestoneByDelta -> handleAdjustMilestone(intent)
            is ScalperIntent.UpdateLadderConfig -> handleUpdateLadderConfig(intent)
            is ScalperIntent.ToggleRatchetPause -> handleToggleRatchetPause(intent)
            is ScalperIntent.ExitPosition -> handleExitPosition(intent)
            is ScalperIntent.PanicExitAll -> handlePanicExitAll()
            is ScalperIntent.CancelAllOrders -> handleCancelAllOrders()

            is ScalperIntent.ConnectFlattrade -> openDailyOtpSheet()
            is ScalperIntent.OpenDailyOtpSheet -> openDailyOtpSheet()
            is ScalperIntent.DismissDailyOtpSheet -> dismissDailyOtpSheet()
            is ScalperIntent.UpdateOtpDigits -> Unit
            is ScalperIntent.SubmitDailyOtp -> Unit
            is ScalperIntent.LaunchBrowserOAuthFallback -> handleLaunchBrowserOAuthFallback()
            is ScalperIntent.DismissAlert -> _uiState.update { it.copy(criticalAlert = null) }
            is ScalperIntent.RefreshHealth -> refreshHealth()
            is ScalperIntent.RefreshOptionChain -> refreshOptionChain()
            is ScalperIntent.RefreshSensexSpot -> refreshSensexSpot()
            is ScalperIntent.DeepLinkReceived -> handleDeepLink(intent)
            is ScalperIntent.ToggleKeepScreenOn -> _uiState.update { it.copy(keepScreenOn = intent.enabled) }
            is ScalperIntent.OpenSettings -> _uiState.update { it.copy(isSettingsOpen = intent.open) }

            is ScalperIntent.OpenTodayTrades -> handleOpenTodayTrades()
            is ScalperIntent.CloseTodayTrades -> _uiState.update { it.copy(isTodayTradesOpen = false) }
            is ScalperIntent.OpenEditLadder -> _uiState.update { it.copy(editingPosition = intent.position) }
        }
    }

    private fun handleOpenTodayTrades() {
        _uiState.update { it.copy(isTodayTradesOpen = true) }
        viewModelScope.launch {
            when (val result = repository.getTodayTrades()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(todayTrades = result.data.activePositions.toPersistentList()) }
                }
                is ApiResult.Error -> {
                    // Retain existing
                }
            }
        }
    }

    private fun handleOneClickBuy(intent: ScalperIntent.OneClickBuy) {
        val actionKey = "BUY_${intent.token}"
        if (!acquireActionLock(actionKey)) return

        // Safety gating for new order entries
        if (!_uiState.value.brokerConnected) {
            viewModelScope.launch {
                _uiEffect.emit(ScalperUiEffect.ShowToast("Order blocked: Broker is disconnected"))
            }
            releaseActionLock(actionKey)
            return
        }
        if (!_uiState.value.tradingEnabled) {
            viewModelScope.launch {
                _uiEffect.emit(ScalperUiEffect.ShowToast("Order entry blocked: TRADING_ENABLED=false"))
            }
            releaseActionLock(actionKey)
            return
        }
        if (_uiState.value.accountStateUncertain) {
            viewModelScope.launch {
                _uiEffect.emit(ScalperUiEffect.ShowToast("Order blocked: Broker account state is uncertain"))
            }
            releaseActionLock(actionKey)
            return
        }
        if (_uiState.value.wsFeedStatus == "FEED_STALE") {
            viewModelScope.launch {
                _uiEffect.emit(ScalperUiEffect.ShowToast("Order blocked: Market feed is stale"))
            }
            releaseActionLock(actionKey)
            return
        }
        if (!hasFreshSelectedTick(intent.marketDataTimestamp, intent.ltpPaise)) {
            viewModelScope.launch {
                _uiEffect.emit(ScalperUiEffect.ShowToast("Order blocked: Selected option has no fresh Flattrade tick"))
            }
            releaseActionLock(actionKey)
            return
        }

        viewModelScope.launch {
            try {
                val draft = _uiState.value.ladderDraft
                val firstTargetPaise = MoneyFormatter.roundDownToTick((draft.firstTargetPts * 100).toLong(), 5L)
                val firstMilestonePaise = MoneyFormatter.roundDownToTick(intent.ltpPaise + firstTargetPaise, 5L)
                val stepSizePaise = max(5L, MoneyFormatter.roundDownToTick((draft.stepPts * 100).toLong(), 5L))
                val breakoutBufferPaise = max(5L, MoneyFormatter.roundDownToTick((draft.breakoutBufferPts * 100).toLong(), 5L))
                val slippageBufferPaise = max(5L, MoneyFormatter.roundDownToTick((draft.slippageBufferPts * 100).toLong(), 5L))

                val productCode = if (_uiState.value.product.equals("NRML", ignoreCase = true)) "M" else "I"

                val result = repository.executeOneClickOrder(
                    token = intent.token,
                    symbol = intent.symbol,
                    exchange = _uiState.value.selectedExchange,
                    lots = _uiState.value.selectedLots,
                    lotSize = intent.lotSize,
                    stepSizePaise = stepSizePaise,
                    breakoutBufferPaise = breakoutBufferPaise,
                    slippageBufferPaise = slippageBufferPaise,
                    firstMilestonePaise = firstMilestonePaise,
                    initialSlPoints = draft.initialSlPts,
                    product = productCode
                )

                when (result) {
                    is ApiResult.Success -> {
                        _uiEffect.emit(ScalperUiEffect.TriggerHaptic())
                        _uiEffect.emit(ScalperUiEffect.ShowToast("1-Click Entry Sent (${intent.symbol})"))
                        _uiState.update {
                            it.copy(
                                lastServerMessage = "Buy order sent: ${intent.symbol}",
                                lastServerMessageType = MessageType.SUCCESS
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Order Failed: ${result.message}"))
                        _uiState.update {
                            it.copy(
                                lastServerMessage = "Order Failed: ${result.message}",
                                lastServerMessageType = MessageType.ERROR
                            )
                        }
                    }
                }
            } finally {
                releaseActionLock(actionKey)
            }
        }
    }

    private fun handleOneClickSell(intent: ScalperIntent.OneClickSell) {
        val actionKey = "SELL_${intent.token}"
        if (!acquireActionLock(actionKey)) return

        val openPosition = _uiState.value.positions.find { it.token == intent.token && it.quantity > 0 }
        val blockedReason = if (openPosition == null) entryBlockedReason(intent.marketDataTimestamp, intent.ltpPaise) else null
        if (blockedReason != null) {
            viewModelScope.launch { _uiEffect.emit(ScalperUiEffect.ShowToast(blockedReason)) }
            releaseActionLock(actionKey)
            return
        }
        if (openPosition != null && !_uiState.value.brokerConnected) {
            viewModelScope.launch { _uiEffect.emit(ScalperUiEffect.ShowToast("Order blocked: Broker is disconnected")) }
            releaseActionLock(actionKey)
            return
        }

        viewModelScope.launch {
            try {
                // If user holds an open position for this token, exit position
                val openPos = openPosition
                if (openPos != null) {
                    when (val result = repository.exitPosition(intent.token)) {
                        is ApiResult.Success -> {
                            _uiEffect.emit(ScalperUiEffect.TriggerHaptic())
                            _uiEffect.emit(ScalperUiEffect.ShowToast("Exit Order Sent (${intent.symbol})"))
                            _uiState.update {
                                it.copy(
                                    lastServerMessage = "Exit order placed for ${intent.symbol}",
                                    lastServerMessageType = MessageType.SUCCESS
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            _uiEffect.emit(ScalperUiEffect.ShowToast("Sell Failed: ${result.message}"))
                            _uiState.update {
                                it.copy(
                                    lastServerMessage = "Exit Failed: ${result.message}",
                                    lastServerMessageType = MessageType.ERROR
                                )
                            }
                        }
                    }
                } else {
                    val draft = _uiState.value.ladderDraft
                    val stepSizePaise = max(5L, MoneyFormatter.roundDownToTick((draft.stepPts * 100).toLong(), 5L))
                    val breakoutBufferPaise = max(5L, MoneyFormatter.roundDownToTick((draft.breakoutBufferPts * 100).toLong(), 5L))
                    val slippageBufferPaise = max(5L, MoneyFormatter.roundDownToTick((draft.slippageBufferPts * 100).toLong(), 5L))
                    val productCode = if (_uiState.value.product.equals("NRML", ignoreCase = true)) "M" else "I"
                    val targetPaise = MoneyFormatter.roundDownToTick((draft.firstTargetPts * 100).toLong(), 5L)
                    val firstMilestonePaise = MoneyFormatter.roundDownToTick(intent.ltpPaise - targetPaise, 5L)
                    if (firstMilestonePaise <= 0L) {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Sell blocked: Target is below the valid option price range"))
                        return@launch
                    }

                    val result = repository.executeOneClickOrder(
                        token = intent.token,
                        symbol = intent.symbol,
                        exchange = _uiState.value.selectedExchange,
                        lots = _uiState.value.selectedLots,
                        lotSize = intent.lotSize,
                        stepSizePaise = stepSizePaise,
                        breakoutBufferPaise = breakoutBufferPaise,
                        slippageBufferPaise = slippageBufferPaise,
                        firstMilestonePaise = firstMilestonePaise,
                        initialSlPoints = draft.initialSlPts,
                        side = "SELL",
                        product = productCode
                    )

                    when (result) {
                        is ApiResult.Success -> {
                            _uiEffect.emit(ScalperUiEffect.TriggerHaptic())
                            _uiEffect.emit(ScalperUiEffect.ShowToast("1-Click Sell Sent (${intent.symbol})"))
                            _uiState.update {
                                it.copy(
                                    lastServerMessage = "Sell order sent: ${intent.symbol}",
                                    lastServerMessageType = MessageType.SUCCESS
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            _uiEffect.emit(ScalperUiEffect.ShowToast("Sell Failed: ${result.message}"))
                            _uiState.update {
                                it.copy(
                                    lastServerMessage = "Sell Failed: ${result.message}",
                                    lastServerMessageType = MessageType.ERROR
                                )
                            }
                        }
                    }
                }
            } finally {
                releaseActionLock(actionKey)
            }
        }
    }

    private fun stepStrike(isCall: Boolean, deltaSteps: Int) {
        val strikes = _uiState.value.strikes
        if (strikes.isEmpty()) return
        val currentStrike = if (isCall) {
            _uiState.value.selectedCallStrike ?: _uiState.value.selectedStrike ?: strikes[strikes.size / 2].strikePrice
        } else {
            _uiState.value.selectedPutStrike ?: _uiState.value.selectedStrike ?: strikes[strikes.size / 2].strikePrice
        }
        val currentIndex = strikes.indexOfFirst { it.strikePrice == currentStrike }
        val targetIndex = (if (currentIndex >= 0) currentIndex + deltaSteps else strikes.size / 2).coerceIn(0, strikes.size - 1)
        val newStrike = strikes[targetIndex].strikePrice
        _uiState.update {
            if (isCall) it.copy(selectedCallStrike = newStrike) else it.copy(selectedPutStrike = newStrike)
        }
    }

    private fun stepLots(delta: Int) {
        val newLots = max(1, _uiState.value.selectedLots + delta)
        _uiState.update { it.copy(selectedLots = newLots) }
    }

    private fun handleAdjustMilestone(intent: ScalperIntent.AdjustMilestoneByDelta) {
        val actionKey = "ADJUST_${intent.token}"
        if (!acquireActionLock(actionKey)) return

        viewModelScope.launch {
            try {
                val currentPos = _uiState.value.positions.find { it.token == intent.token }
                val currentMilestone = currentPos?.localNextMilestonePaise ?: return@launch
                val newMilestone = max(5L, currentMilestone + intent.deltaPaise)
                val aligned = MoneyFormatter.roundDownToTick(newMilestone, 5L)

                when (val result = repository.patchLadder(token = intent.token, nextMilestonePaise = aligned)) {
                    is ApiResult.Success -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Milestone: ${MoneyFormatter.formatPaise(aligned)}"))
                    }
                    is ApiResult.Error -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Adjustment Failed: ${result.message}"))
                    }
                }
            } finally {
                releaseActionLock(actionKey)
            }
        }
    }

    private fun handleUpdateLadderConfig(intent: ScalperIntent.UpdateLadderConfig) {
        val actionKey = "CONFIG_${intent.token}"
        if (!acquireActionLock(actionKey)) return

        viewModelScope.launch {
            try {
                repository.patchLadder(
                    token = intent.token,
                    nextMilestonePaise = intent.nextMilestonePaise,
                    stepSizePaise = intent.stepSizePaise,
                    breakoutBufferPaise = intent.breakoutBufferPaise,
                    slippageBufferPaise = intent.slippageBufferPaise
                )
            } finally {
                releaseActionLock(actionKey)
            }
        }
    }

    private fun handleToggleRatchetPause(intent: ScalperIntent.ToggleRatchetPause) {
        val actionKey = "PAUSE_${intent.token}"
        if (!acquireActionLock(actionKey)) return

        viewModelScope.launch {
            try {
                repository.patchLadder(token = intent.token, paused = intent.isPaused)
            } finally {
                releaseActionLock(actionKey)
            }
        }
    }

    private fun handleExitPosition(intent: ScalperIntent.ExitPosition) {
        val actionKey = "EXIT_${intent.token}"
        if (!acquireActionLock(actionKey)) return

        viewModelScope.launch {
            try {
                when (val result = repository.exitPosition(intent.token)) {
                    is ApiResult.Success -> {
                        _uiEffect.emit(ScalperUiEffect.TriggerHaptic())
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Exit Order Placed (${intent.token})"))
                    }
                    is ApiResult.Error -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Exit Failed: ${result.message}"))
                    }
                }
            } finally {
                releaseActionLock(actionKey)
            }
        }
    }

    private fun handlePanicExitAll() {
        val actionKey = "EXIT_ALL"
        if (!acquireActionLock(actionKey)) return

        viewModelScope.launch {
            try {
                _uiEffect.emit(ScalperUiEffect.TriggerHaptic(isImportant = true))
                when (val result = repository.panicExitAll()) {
                    is ApiResult.Success -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("PANIC EXIT EXECUTED FOR ALL POSITIONS"))
                        _uiState.update {
                            it.copy(
                                lastServerMessage = "Panic exit executed for all positions",
                                lastServerMessageType = MessageType.SUCCESS
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Panic Exit Error: ${result.message}"))
                        _uiState.update {
                            it.copy(
                                lastServerMessage = "Panic Exit Error: ${result.message}",
                                lastServerMessageType = MessageType.ERROR
                            )
                        }
                    }
                }
            } finally {
                releaseActionLock(actionKey)
            }
        }
    }

    private fun handleCancelAllOrders() {
        val actionKey = "CANCEL_ALL"
        if (!acquireActionLock(actionKey)) return

        viewModelScope.launch {
            try {
                _uiEffect.emit(ScalperUiEffect.TriggerHaptic(isImportant = true))
                when (val result = repository.panicExitAll()) {
                    is ApiResult.Success -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("ALL ORDERS CANCELLED"))
                        _uiState.update {
                            it.copy(
                                lastServerMessage = "All orders cancelled successfully",
                                lastServerMessageType = MessageType.INFO
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Cancel Error: ${result.message}"))
                        _uiState.update {
                            it.copy(
                                lastServerMessage = "Cancel Error: ${result.message}",
                                lastServerMessageType = MessageType.ERROR
                            )
                        }
                    }
                }
            } finally {
                releaseActionLock(actionKey)
            }
        }
    }

    private fun openDailyOtpSheet() {
        _uiState.update {
            it.copy(
                dailyOtpSheet = DailyOtpSheetState(
                    isVisible = true,
                    otpDigits = "",
                    isSubmitting = false,
                    errorMessage = null,
                    suggestBrowserFallback = false
                )
            )
        }
    }

    private fun dismissDailyOtpSheet() {
        _uiState.update {
            it.copy(
                dailyOtpSheet = it.dailyOtpSheet.copy(
                    isVisible = false,
                    otpDigits = "",
                    errorMessage = null
                )
            )
        }
    }

    private fun handleLaunchBrowserOAuthFallback() {
        dismissDailyOtpSheet()
        viewModelScope.launch {
            when (val result = repository.getLoginUrl()) {
                is ApiResult.Success -> {
                    _uiEffect.emit(ScalperUiEffect.OpenCustomTab(result.data))
                }
                is ApiResult.Error -> {
                    _uiEffect.emit(ScalperUiEffect.ShowToast("Could not fetch OAuth URL: ${result.message}"))
                }
            }
        }
    }

    private fun handleDeepLink(intent: ScalperIntent.DeepLinkReceived) {
        val uri = intent.uri
        val path = uri.path ?: ""
        if (path.endsWith("/success")) {
            _uiEffect.tryEmit(ScalperUiEffect.ShowToast("Flattrade Authenticated Successfully!"))
            refreshHealth()
            reconnectFeed()
            refreshSensexSpot()
        } else if (path.endsWith("/error")) {
            val errorMsg = uri.getQueryParameter("message") ?: "OAuth error"
            _uiEffect.tryEmit(ScalperUiEffect.ShowToast("Authentication Failed: $errorMsg"))
        }
    }

    private fun selectIndex(index: String) {
        optionChainRefreshJob?.cancel()
        optionChainRefreshJob = null
        _uiState.update {
            it.copy(
                selectedIndex = index,
                selectedExchange = if (index == "SENSEX") "BFO" else "NFO",
                // Spot and contract metadata must come from broker market/instrument data.
                // Clear the prior index value so an old spot cannot select the wrong ATM.
                spotLtpPaise = 0L,
                spotStatus = "WAITING_FOR_TICK",
                marketDataTimestamp = null,
                marketDataError = null,
                strikes = persistentListOf(),
                availableExpiries = persistentListOf(),
                selectedExpiry = "",
                selectedStrike = null,
                selectedCallStrike = null,
                selectedPutStrike = null
            )
        }
        refreshSensexSpot()
    }

    fun refreshHealth() {
        if (_uiState.value.backendReachability != BACKEND_ONLINE) {
            checkBackendConnectivity()
            return
        }
        viewModelScope.launch {
            when (val result = repository.getAuthStatus()) {
                is ApiResult.Success -> {
                    val s = result.data
                    _uiState.update {
                        it.copy(
                            brokerConnected = s.brokerConnected,
                            sessionTokenPresent = s.sessionTokenPresent,
                            sessionValid = s.sessionValid,
                            sessionValidity = s.sessionValidity,
                            wsFeedHealthy = s.wsFeedHealthy,
                            tradingEnabled = s.tradingEnabled,
                            wsFeedStatus = s.wsFeedStatus,
                            accountStateUncertain = s.accountStateUncertain
                        )
                    }
                }
                is ApiResult.Error -> {
                    when (val healthResult = repository.checkHealth()) {
                        is ApiResult.Success -> {
                            val h = healthResult.data
                            _uiState.update {
                                it.copy(
                                    backendReachability = BACKEND_ONLINE,
                                    brokerConnected = h.brokerConnected,
                                    wsFeedHealthy = h.wsFeedHealthy,
                                    tradingEnabled = h.tradingEnabled,
                                    wsFeedStatus = h.wsFeedStatus,
                                    accountStateUncertain = h.accountStateUncertain,
                                    sessionTokenPresent = h.sessionTokenPresent,
                                    sessionValid = h.sessionValid,
                                    sessionValidity = h.sessionValidity
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    backendReachability = BACKEND_OFFLINE,
                                    brokerConnected = false,
                                    sessionValid = false,
                                    sessionValidity = "UNKNOWN",
                                    wsFeedHealthy = false,
                                    tradingEnabled = false,
                                    wsFeedStatus = "OFFLINE",
                                    lastServerMessage = "${ServerConfig.environment} backend unreachable: ${healthResult.message}",
                                    lastServerMessageType = MessageType.ERROR
                                )
                            }
                            checkBackendConnectivity()
                        }
                    }
                }
            }
        }
    }

    fun reconnectFeed() {
        if (_uiState.value.backendReachability == BACKEND_ONLINE) {
            repository.connectWebSocket()
        } else {
            checkBackendConnectivity()
        }
    }

    private fun checkBackendConnectivity() {
        if (backendConnectivityJob?.isActive == true) return
        backendConnectivityJob = viewModelScope.launch {
            var retryDelayMs = BACKEND_INITIAL_RETRY_DELAY_MS
            while (true) {
                when (val result = repository.checkHealth()) {
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(
                                backendReachability = BACKEND_ONLINE,
                                lastServerMessage = "${ServerConfig.environment} backend reachable",
                                lastServerMessageType = MessageType.SUCCESS
                            )
                        }
                        repository.connectWebSocket()
                        refreshHealth()
                        refreshSensexSpot()
                        return@launch
                    }
                    is ApiResult.Error -> {
                        val retryable = result.statusCode == null || result.statusCode >= 500
                        _uiState.update {
                            it.copy(
                                backendReachability = if (retryable) BACKEND_RETRYING else BACKEND_OFFLINE,
                                brokerConnected = false,
                                wsFeedHealthy = false,
                                tradingEnabled = false,
                                wsFeedStatus = "OFFLINE",
                                lastServerMessage = "${ServerConfig.environment} backend unreachable: ${result.message}",
                                lastServerMessageType = MessageType.ERROR
                            )
                        }
                        if (!retryable) return@launch
                        delay(retryDelayMs)
                        retryDelayMs = (retryDelayMs * 2).coerceAtMost(BACKEND_MAX_RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    fun refreshOptionChain(force: Boolean = true) {
        val now = SystemClock.elapsedRealtime()
        if (!force && lastOptionChainAttemptAt > 0L && now - lastOptionChainAttemptAt < 5_000L) return
        if (!force && optionChainRefreshJob?.isActive == true) return
        lastOptionChainAttemptAt = now
        optionChainRefreshJob?.cancel()
        optionChainRefreshJob = viewModelScope.launch {
            val current = _uiState.value
            val symbol = current.selectedIndex
            val expiry = current.selectedExpiry
            when (val result = repository.getOptionChain(symbol, expiry)) {
                is ApiResult.Success -> {
                    val chain = result.data
                    if (_uiState.value.selectedIndex != symbol ||
                        (_uiState.value.selectedExpiry.isNotBlank() && _uiState.value.selectedExpiry != expiry)) {
                        return@launch
                    }
                    val strikesList = chain.strikes.map { strike ->
                        OptionStrikeUi(
                            strikePrice = strike.strikePrice,
                            ceToken = strike.ceToken,
                            ceSymbol = strike.ceSymbol,
                            ceLotSize = strike.ceLotSize,
                            ceLtpPaise = strike.ceLtpPaise,
                            ceTimestamp = strike.ceTimestamp,
                            peToken = strike.peToken,
                            peSymbol = strike.peSymbol,
                            peLotSize = strike.peLotSize,
                            peLtpPaise = strike.peLtpPaise,
                            peTimestamp = strike.peTimestamp
                        )
                    }.toPersistentList()

                    val atmStrike = chain.atmStrike?.takeIf { strike ->
                        strikesList.any { it.strikePrice == strike && !it.ceToken.isNullOrBlank() && !it.peToken.isNullOrBlank() }
                    }
                    val latest = _uiState.value
                    val defaultStrike = latest.selectedStrike?.takeIf { selected -> strikesList.any { it.strikePrice == selected } } ?: atmStrike
                    val callStrike = latest.selectedCallStrike?.takeIf { selected -> strikesList.any { it.strikePrice == selected && !it.ceToken.isNullOrBlank() } } ?: atmStrike
                    val putStrike = latest.selectedPutStrike?.takeIf { selected -> strikesList.any { it.strikePrice == selected && !it.peToken.isNullOrBlank() } } ?: atmStrike
                    val callLotSize = strikesList.find { it.strikePrice == callStrike }?.ceLotSize
                    val putLotSize = strikesList.find { it.strikePrice == putStrike }?.peLotSize

                    _uiState.update {
                        it.copy(
                            selectedExchange = chain.exchange,
                            selectedExpiry = chain.expiry,
                            availableExpiries = chain.expiries.toPersistentList(),
                            strikes = strikesList,
                            selectedStrike = defaultStrike,
                            selectedCallStrike = callStrike,
                            selectedPutStrike = putStrike,
                            lotSize = callLotSize ?: putLotSize ?: 0,
                            marketDataError = null,
                            lastServerMessage = "-",
                            lastServerMessageType = MessageType.INFO
                        )
                    }
                }
                is ApiResult.Error -> {
                    if (_uiState.value.selectedIndex != symbol ||
                        _uiState.value.selectedExpiry != expiry) return@launch
                    _uiState.update {
                        it.copy(
                            strikes = persistentListOf(),
                            selectedStrike = null,
                            selectedCallStrike = null,
                            selectedPutStrike = null,
                            marketDataError = result.message,
                            lastServerMessage = result.message,
                            lastServerMessageType = MessageType.ERROR
                        )
                    }
                }
            }
        }
    }

    fun refreshSensexSpot() {
        if (_uiState.value.backendReachability != BACKEND_ONLINE) {
            checkBackendConnectivity()
            return
        }
        val symbol = _uiState.value.selectedIndex
        spotRetryDelayMs = SPOT_INITIAL_RETRY_DELAY_MS
        spotRefreshJob?.cancel()
        spotRefreshJob = viewModelScope.launch {
            while (_uiState.value.selectedIndex == symbol) {
                var retry = false
                when (val result = repository.getIndexSpot(symbol)) {
                    is ApiResult.Success -> {
                        val spot = result.data
                        val readyLtp = spot.spotLtpPaise?.takeIf {
                            spot.status == "READY" && spot.marketDataReady && it > 0L
                        }
                        _uiState.update {
                            it.copy(
                                spotLtpPaise = readyLtp ?: 0L,
                                spotStatus = if (readyLtp != null) "READY" else spot.status,
                                marketDataTimestamp = spot.timestamp,
                                feedConnectionStatus = if (readyLtp != null) "CONNECTED" else spot.status,
                                marketDataError = if (readyLtp == null) {
                                    spot.error
                                        ?: when (spot.status) {
                                            "INSTRUMENT_UNAVAILABLE" -> "Flattrade index token resolution failed."
                                            "STALE" -> "Flattrade spot tick is stale."
                                            else -> "Waiting for a fresh Flattrade spot tick."
                                        }
                                } else null
                            )
                        }
                        if (readyLtp != null && _uiState.value.strikes.isEmpty()) {
                            refreshOptionChain(force = false)
                        }
                        if (readyLtp != null) spotRetryDelayMs = SPOT_INITIAL_RETRY_DELAY_MS
                        retry = readyLtp == null && spot.status in setOf(
                            "WAITING_FOR_TICK",
                            "STALE",
                            "RECONNECTING",
                            "DISCONNECTED",
                            "WEBSOCKET_NOT_CONNECTED",
                            "SUBSCRIPTION_REJECTED",
                            "BROKER_API_FAILURE"
                        )
                    }
                    is ApiResult.Error -> {
                        val status = when (result.errorCode) {
                            "BROKER_SESSION_UNAVAILABLE" -> "BROKER_SESSION_UNAVAILABLE"
                            "INDEX_NOT_FOUND", "INVALID_INDEX_TOKEN", "AMBIGUOUS_INDEX", "INVALID_INDEX_EXCHANGE" ->
                                "INSTRUMENT_UNAVAILABLE"
                            "WEBSOCKET_NOT_CONNECTED" -> "DISCONNECTED"
                            "SUBSCRIPTION_REJECTED" -> "SUBSCRIPTION_REJECTED"
                            "STALE_MARKET_DATA" -> "STALE"
                            else -> "UNAVAILABLE"
                        }
                        _uiState.update {
                            it.copy(
                                spotLtpPaise = 0L,
                                spotStatus = status,
                                feedConnectionStatus = status,
                                marketDataError = result.message,
                                lastServerMessage = result.message,
                                lastServerMessageType = MessageType.ERROR
                            )
                        }
                        retry = result.errorCode != "BROKER_SESSION_UNAVAILABLE" &&
                            ((result.errorCode != null && result.errorCode in RETRYABLE_SPOT_ERROR_CODES) ||
                                result.statusCode == null ||
                                result.statusCode >= 500)
                    }
                }
                if (!retry) break
                val retryDelayMs = spotRetryDelayMs
                delay(retryDelayMs)
                spotRetryDelayMs = (retryDelayMs * 2).coerceAtMost(SPOT_MAX_RETRY_DELAY_MS)
            }
        }
    }

    /**
     * Atomically enforces:
     * 1. 500 ms tap cooldown per action key.
     * 2. Rejection if command is already in inFlightCommands.
     */
    private fun entryBlockedReason(timestamp: Long?, ltpPaise: Long): String? {
        val state = _uiState.value
        if (!state.brokerConnected) return "Order blocked: Broker is disconnected"
        if (!state.tradingEnabled) return "Order entry blocked: TRADING_ENABLED=false"
        if (state.accountStateUncertain) return "Order blocked: Broker account state is uncertain"
        if (state.feedConnectionStatus != "CONNECTED" || state.wsFeedStatus == "FEED_STALE") {
            return "Order blocked: Flattrade market-data feed is not live"
        }
        val ageMs = timestamp?.let { System.currentTimeMillis() - it }
        if (ltpPaise <= 0L || ageMs == null || ageMs !in 0L..3_000L) {
            return "Order blocked: Selected option has no fresh Flattrade tick"
        }
        return null
    }

    private fun hasFreshSelectedTick(timestamp: Long?, ltpPaise: Long): Boolean =
        entryBlockedReason(timestamp, ltpPaise) == null

    private fun acquireActionLock(actionKey: String): Boolean {
        val now = clock()
        val lastTap = lastTapTimestamps[actionKey] ?: 0L
        if (now - lastTap < TAP_COOLDOWN_MS) {
            return false // In cooldown
        }

        if (_uiState.value.inFlightCommands.contains(actionKey)) {
            return false // Already in flight
        }

        lastTapTimestamps[actionKey] = now
        _uiState.update { it.copy(inFlightCommands = it.inFlightCommands + actionKey) }
        return true
    }

    private fun releaseActionLock(actionKey: String) {
        _uiState.update { it.copy(inFlightCommands = it.inFlightCommands - actionKey) }
    }

    companion object {
        const val TAP_COOLDOWN_MS = 500L
        private const val BACKEND_ONLINE = "ONLINE"
        private const val BACKEND_RETRYING = "RETRYING"
        private const val BACKEND_OFFLINE = "OFFLINE"
        private const val BACKEND_INITIAL_RETRY_DELAY_MS = 1_000L
        private const val BACKEND_MAX_RETRY_DELAY_MS = 30_000L
        private const val SPOT_INITIAL_RETRY_DELAY_MS = 1_000L
        private const val SPOT_MAX_RETRY_DELAY_MS = 30_000L
        private val RETRYABLE_SPOT_ERROR_CODES = setOf(
            "BROKER_API_FAILURE",
            "INVALID_INDEX_RESPONSE",
            "WEBSOCKET_NOT_CONNECTED",
            "SUBSCRIPTION_REJECTED",
            "STALE_MARKET_DATA",
            "INITIAL_TICK_NOT_RECEIVED"
        )
    }
}
