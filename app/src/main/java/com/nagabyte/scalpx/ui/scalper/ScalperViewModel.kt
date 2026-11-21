package com.nagabyte.scalpx.ui.scalper

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.data.remote.WsConnectionState
import com.nagabyte.scalpx.data.repository.ApiResult
import com.nagabyte.scalpx.data.repository.TradingRepository
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    init {
        observeRepositoryStreams()
        refreshHealth()
        loadPresetsFromConfig()
        refreshOptionChain()
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
            repository.pingMs.collect { ping ->
                _uiState.update { it.copy(pingMs = ping) }
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

    fun handleIntent(intent: ScalperIntent) {
        when (intent) {
            is ScalperIntent.SelectIndex -> selectIndex(intent.index)
            is ScalperIntent.SelectExpiry -> _uiState.update { it.copy(selectedExpiry = intent.expiry) }
            is ScalperIntent.SelectStrike -> _uiState.update { it.copy(selectedStrike = intent.strike) }
            is ScalperIntent.SelectLots -> _uiState.update { it.copy(selectedLots = intent.lots) }
            is ScalperIntent.SelectProduct -> _uiState.update { it.copy(product = intent.product) }
            is ScalperIntent.UpdateLadderDraft -> _uiState.update { it.copy(ladderDraft = intent.draft) }

            is ScalperIntent.OneClickBuy -> handleOneClickBuy(intent)
            is ScalperIntent.AdjustMilestoneByDelta -> handleAdjustMilestone(intent)
            is ScalperIntent.UpdateLadderConfig -> handleUpdateLadderConfig(intent)
            is ScalperIntent.ToggleRatchetPause -> handleToggleRatchetPause(intent)
            is ScalperIntent.ExitPosition -> handleExitPosition(intent)
            is ScalperIntent.PanicExitAll -> handlePanicExitAll()

            is ScalperIntent.ConnectFlattrade -> handleConnectFlattrade()
            is ScalperIntent.DismissAlert -> _uiState.update { it.copy(criticalAlert = null) }
            is ScalperIntent.RefreshHealth -> refreshHealth()
            is ScalperIntent.RefreshOptionChain -> refreshOptionChain()
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
                    exchange = "NFO",
                    lots = _uiState.value.selectedLots,
                    lotSize = intent.lotSize,
                    stepSizePaise = stepSizePaise,
                    breakoutBufferPaise = breakoutBufferPaise,
                    slippageBufferPaise = slippageBufferPaise,
                    firstMilestonePaise = firstMilestonePaise,
                    initialSlPoints = draft.initialSlPts
                )

                when (result) {
                    is ApiResult.Success -> {
                        _uiEffect.emit(ScalperUiEffect.TriggerHaptic())
                        _uiEffect.emit(ScalperUiEffect.ShowToast("1-Click Entry Sent (${intent.symbol})"))
                    }
                    is ApiResult.Error -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Order Failed: ${result.message}"))
                    }
                }
            } finally {
                releaseActionLock(actionKey)
            }
        }
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
                    }
                    is ApiResult.Error -> {
                        _uiEffect.emit(ScalperUiEffect.ShowToast("Panic Exit Error: ${result.message}"))
                    }
                }
            } finally {
                releaseActionLock(actionKey)
            }
        }
    }

    private fun handleConnectFlattrade() {
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
            refreshOptionChain()
        } else if (path.endsWith("/error")) {
            val errorMsg = uri.getQueryParameter("message") ?: "OAuth error"
            _uiEffect.tryEmit(ScalperUiEffect.ShowToast("Authentication Failed: $errorMsg"))
        }
    }

    private fun selectIndex(index: String) {
        val lotSize = when (index) {
            "BANKNIFTY" -> 15
            "SENSEX" -> 10
            else -> 25 // NIFTY
        }
        _uiState.update { it.copy(selectedIndex = index, lotSize = lotSize) }
        refreshOptionChain()
    }

    fun refreshHealth() {
        viewModelScope.launch {
            when (val result = repository.checkHealth()) {
                is ApiResult.Success -> {
                    val h = result.data
                    _uiState.update {
                        it.copy(
                            brokerMode = h.brokerMode.uppercase(),
                            brokerConnected = h.brokerConnected,
                            wsFeedHealthy = h.wsFeedHealthy
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            brokerConnected = false,
                            wsFeedHealthy = false
                        )
                    }
                }
            }
        }
    }

    fun reconnectFeed() {
        repository.connectWebSocket()
    }

    fun refreshOptionChain() {
        viewModelScope.launch {
            val symbol = _uiState.value.selectedIndex
            val expiry = _uiState.value.selectedExpiry
            when (val result = repository.getOptionChain(symbol, expiry)) {
                is ApiResult.Success -> {
                    val strikesList = result.data.strikes.map { s ->
                        OptionStrikeUi(
                            strikePrice = s.strikePrice,
                            ceToken = s.ceToken,
                            ceSymbol = s.ceSymbol,
                            ceLtpPaise = s.ceLtpPaise,
                            peToken = s.peToken,
                            peSymbol = s.peSymbol,
                            peLtpPaise = s.peLtpPaise
                        )
                    }.toPersistentList()
                    val defaultStrike = _uiState.value.selectedStrike ?: strikesList.getOrNull(strikesList.size / 2)?.strikePrice
                    _uiState.update {
                        it.copy(
                            selectedExpiry = result.data.expiry,
                            strikes = strikesList,
                            selectedStrike = defaultStrike
                        )
                    }
                }
                is ApiResult.Error -> {
                    // Retain existing chain
                }
            }
        }
    }

    /**
     * Atomically enforces:
     * 1. 500 ms tap cooldown per action key.
     * 2. Rejection if command is already in inFlightCommands.
     */
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
    }
}
