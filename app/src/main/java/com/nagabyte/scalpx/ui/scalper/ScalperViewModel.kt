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

    fun handleIntent(intent: ScalperIntent) {
        when (intent) {
            is ScalperIntent.SelectIndex -> selectIndex(intent.index)
            is ScalperIntent.SelectExpiry -> _uiState.update { it.copy(selectedExpiry = intent.expiry) }
            is ScalperIntent.SelectStrike -> _uiState.update { it.copy(selectedStrike = intent.strike, selectedCallStrike = intent.strike, selectedPutStrike = intent.strike) }
            is ScalperIntent.SelectCallStrike -> _uiState.update { it.copy(selectedCallStrike = intent.strike) }
            is ScalperIntent.SelectPutStrike -> _uiState.update { it.copy(selectedPutStrike = intent.strike) }
            is ScalperIntent.StepStrike -> stepStrike(intent.isCall, intent.deltaSteps)

            is ScalperIntent.SelectLots -> _uiState.update { it.copy(selectedLots = intent.lots) }
            is ScalperIntent.StepLots -> stepLots(intent.delta)
            is ScalperIntent.SelectProduct -> _uiState.update { it.copy(product = intent.product) }

            is ScalperIntent.ToggleOneClick -> _uiState.update { it.copy(oneClickEnabled = intent.enabled) }
            is ScalperIntent.ToggleZone2Collapse -> _uiState.update { it.copy(isZone2Collapsed = intent.collapsed) }
            is ScalperIntent.SelectBookTab -> _uiState.update { it.copy(activeTab = intent.tab) }
            is ScalperIntent.ToggleTableView -> _uiState.update { it.copy(isTableView = intent.isTable) }
            is ScalperIntent.ToggleTheme -> _uiState.update { it.copy(isDarkTheme = intent.isDark) }
            is ScalperIntent.OpenRatchetConfig -> _uiState.update { it.copy(isRatchetConfigOpen = intent.open) }
            is ScalperIntent.DismissConfirmation -> _uiState.update { it.copy(pendingConfirmation = null) }

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
            is ScalperIntent.UpdateOtpDigits -> updateOtpDigits(intent.digits)
            is ScalperIntent.SubmitDailyOtp -> submitDailyOtp()
            is ScalperIntent.LaunchBrowserOAuthFallback -> handleLaunchBrowserOAuthFallback()
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

        viewModelScope.launch {
            try {
                // If user holds an open position for this token, exit position
                val openPos = _uiState.value.positions.find { it.token == intent.token && it.quantity > 0 }
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

                    val result = repository.executeOneClickOrder(
                        token = intent.token,
                        symbol = intent.symbol,
                        exchange = "NFO",
                        lots = _uiState.value.selectedLots,
                        lotSize = intent.lotSize,
                        stepSizePaise = stepSizePaise,
                        breakoutBufferPaise = breakoutBufferPaise,
                        slippageBufferPaise = slippageBufferPaise,
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

    private fun updateOtpDigits(digits: String) {
        val filtered = digits.filter { it.isDigit() }.take(6)
        _uiState.update {
            it.copy(
                dailyOtpSheet = it.dailyOtpSheet.copy(
                    otpDigits = filtered,
                    errorMessage = null
                )
            )
        }

        if (filtered.length == 6 && !_uiState.value.dailyOtpSheet.isSubmitting) {
            submitDailyOtp()
        }
    }

    private fun submitDailyOtp() {
        val currentOtp = _uiState.value.dailyOtpSheet.otpDigits
        if (currentOtp.length != 6 || _uiState.value.dailyOtpSheet.isSubmitting) {
            return
        }

        _uiState.update {
            it.copy(
                dailyOtpSheet = it.dailyOtpSheet.copy(
                    isSubmitting = true,
                    errorMessage = null
                )
            )
        }

        viewModelScope.launch {
            when (val result = repository.submitDailyOtp(currentOtp)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            dailyOtpSheet = DailyOtpSheetState(isVisible = false, otpDigits = "")
                        )
                    }
                    _uiEffect.emit(ScalperUiEffect.TriggerHaptic())
                    _uiEffect.emit(ScalperUiEffect.ShowToast("Daily login successful"))
                    refreshHealth()
                    reconnectFeed()
                    refreshOptionChain()
                }
                is ApiResult.Error -> {
                    val isInvalidOtp = result.errorCode == "INVALID_OTP" || result.statusCode == 401
                    val isFallback = result.errorCode == "FALLBACK_REQUIRED" || result.statusCode == 502

                    _uiState.update { current ->
                        current.copy(
                            dailyOtpSheet = current.dailyOtpSheet.copy(
                                isSubmitting = false,
                                otpDigits = if (isInvalidOtp) "" else current.dailyOtpSheet.otpDigits,
                                errorMessage = result.message,
                                suggestBrowserFallback = if (isFallback) true else current.dailyOtpSheet.suggestBrowserFallback
                            )
                        )
                    }

                    if (isInvalidOtp) {
                        _uiEffect.emit(ScalperUiEffect.TriggerHaptic(isImportant = true))
                    }
                }
            }
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
                            wsFeedHealthy = h.wsFeedHealthy,
                            tradingEnabled = h.tradingEnabled,
                            wsFeedStatus = h.wsFeedStatus,
                            accountStateUncertain = h.accountStateUncertain,
                            userId = repository.configStore.getDeviceId()
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            brokerConnected = false,
                            wsFeedHealthy = false,
                            tradingEnabled = false,
                            wsFeedStatus = "OFFLINE"
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
                    val callStrike = _uiState.value.selectedCallStrike ?: defaultStrike
                    val putStrike = _uiState.value.selectedPutStrike ?: defaultStrike
                    val spotPaise = defaultStrike?.let { (it * 100).toLong() } ?: 2452080L

                    _uiState.update {
                        it.copy(
                            selectedExpiry = result.data.expiry,
                            strikes = strikesList,
                            selectedStrike = defaultStrike,
                            selectedCallStrike = callStrike,
                            selectedPutStrike = putStrike,
                            spotLtpPaise = spotPaise
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
