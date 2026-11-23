package com.nagabyte.scalpx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nagabyte.scalpx.ui.auth.DailyOtpSheet
import com.nagabyte.scalpx.ui.components.*
import com.nagabyte.scalpx.ui.scalper.*
import com.nagabyte.scalpx.ui.theme.LocalScalperTheme
import com.nagabyte.scalpx.ui.theme.ScalperTheme
import com.nagabyte.scalpx.ui.theme.getScalperColors
import kotlinx.coroutines.delay

/**
 * 1Cliq-style Options Scalping Cockpit for Android portrait screens (360dp - 430dp).
 * Zero text clipping, zero horizontal overflow, and minimum 48dp touch targets on execution deck.
 */
@Composable
fun ScalperCockpitScreen(
    state: ScalperUiState,
    onIntent: (ScalperIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTheme = getScalperColors(state.isDarkTheme)

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            onIntent(ScalperIntent.RefreshHealth)
        }
    }

    CompositionLocalProvider(LocalScalperTheme provides currentTheme) {
        val colors = ScalperTheme.colors

        // Resolve active Call and Put strikes
        val callStrikeUi = remember(state.strikes, state.selectedCallStrike) {
            state.strikes.find { it.strikePrice == state.selectedCallStrike }
                ?: state.strikes.firstOrNull { it.ceToken != null }
        }

        val putStrikeUi = remember(state.strikes, state.selectedPutStrike) {
            state.strikes.find { it.strikePrice == state.selectedPutStrike }
                ?: state.strikes.firstOrNull { it.peToken != null }
        }

        val netQty = remember(state.positions) {
            state.positions.sumOf { it.quantity }
        }

        val totalMtmPaise = remember(state.totalUnrealizedPnlPaise, state.totalRealizedPnlPaise) {
            state.totalUnrealizedPnlPaise + state.totalRealizedPnlPaise
        }

        fun executeOrder(
            side: String,
            token: String,
            symbol: String,
            ltpPaise: Long,
            lotSize: Int,
            marketDataTimestamp: Long?
        ) {
            if (side == "BUY") {
                onIntent(ScalperIntent.OneClickBuy(token, symbol, ltpPaise, marketDataTimestamp, lotSize))
            } else {
                onIntent(ScalperIntent.OneClickSell(token, symbol, ltpPaise, marketDataTimestamp, lotSize))
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .background(colors.background)
        ) {
            // ZONE 1: Pinned Top Header Bar
            Zone1BrokerHeader(
                serverWsState = state.serverWsState,
                brokerConnected = state.brokerConnected,
                wsFeedHealthy = state.wsFeedHealthy,
                pingMs = state.pingMs,
                isDarkTheme = state.isDarkTheme,
                onConnectOtp = { onIntent(ScalperIntent.OpenDailyOtpSheet) },
                onToggleTheme = { onIntent(ScalperIntent.ToggleTheme(it)) },
                onOpenSettings = { onIntent(ScalperIntent.OpenSettings(true)) }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(colors.background)
                    .verticalScroll(rememberScrollState())
            ) {
                // ZONE 7: Emergency Alert Banner (if active)
                state.criticalAlert?.let { alert ->
                    ManualInterventionBanner(
                        alert = alert,
                        onDismiss = { onIntent(ScalperIntent.DismissAlert) }
                    )
                }

                // TOP EXECUTION CLUSTER (Compact ~40-45% Screen Height)
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ZONE 2: Parameter Selector Grid + Ratchet SL Pill
                    Zone2ParameterGrid(
                        selectedIndex = state.selectedIndex,
                        selectedExpiry = state.selectedExpiry,
                        availableExpiries = state.availableExpiries,
                        product = state.product,
                        selectedCallStrike = state.selectedCallStrike,
                        selectedPutStrike = state.selectedPutStrike,
                        selectedLots = state.selectedLots,
                        lotSize = state.lotSize,
                        ladderDraft = state.ladderDraft,
                        isCollapsed = state.isZone2Collapsed,
                        onSelectIndex = { onIntent(ScalperIntent.SelectIndex(it)) },
                        onSelectExpiry = { onIntent(ScalperIntent.SelectExpiry(it)) },
                        onSelectProduct = { onIntent(ScalperIntent.SelectProduct(it)) },
                        onStepCallStrike = { delta -> onIntent(ScalperIntent.StepStrike(isCall = true, deltaSteps = delta)) },
                        onStepPutStrike = { delta -> onIntent(ScalperIntent.StepStrike(isCall = false, deltaSteps = delta)) },
                        onStepLots = { delta -> onIntent(ScalperIntent.StepLots(delta)) },
                        onToggleCollapse = { onIntent(ScalperIntent.ToggleZone2Collapse(it)) },
                        onOpenRatchetConfig = { onIntent(ScalperIntent.OpenRatchetConfig(true)) }
                    )

                    HorizontalDivider(color = colors.border, thickness = 1.dp)

                    // ZONE 3: Spot Index Ticker + Panic Actions + Server Message Strip
                    Zone3SpotPanicHub(
                        selectedIndex = state.selectedIndex,
                        spotLtpPaise = state.spotLtpPaise,
                        spotStatus = state.spotStatus,
                        marketDataError = state.marketDataError,
                        spotChangePts = state.spotChangePts,
                        spotChangePercent = state.spotChangePercent,
                        lastServerMessage = state.lastServerMessage,
                        lastServerMessageType = state.lastServerMessageType,
                        onCloseAllPositions = { onIntent(ScalperIntent.PanicExitAll) },
                        onCancelAllOrders = { onIntent(ScalperIntent.CancelAllOrders) },
                        onRetrySpot = { onIntent(ScalperIntent.RefreshSensexSpot) }
                    )

                    HorizontalDivider(color = colors.border, thickness = 1.dp)

                    // ZONE 4: Dual CE/PE Execution Deck (50/50 Dual Deck, Touch Targets >= 48dp)
                    Zone4ExecutionDeck(
                        callStrikeUi = callStrikeUi,
                        putStrikeUi = putStrikeUi,
                        lotSize = state.lotSize,
                        inFlightCommands = state.inFlightCommands,
                        onBuyCall = { token, symbol, ltp ->
                            executeOrder("BUY", token, symbol, ltp, callStrikeUi?.ceLotSize ?: state.lotSize, callStrikeUi?.ceTimestamp)
                        },
                        onSellCall = { token, symbol, ltp ->
                            executeOrder("SELL", token, symbol, ltp, callStrikeUi?.ceLotSize ?: state.lotSize, callStrikeUi?.ceTimestamp)
                        },
                        onBuyPut = { token, symbol, ltp ->
                            executeOrder("BUY", token, symbol, ltp, putStrikeUi?.peLotSize ?: state.lotSize, putStrikeUi?.peTimestamp)
                        },
                        onSellPut = { token, symbol, ltp ->
                            executeOrder("SELL", token, symbol, ltp, putStrikeUi?.peLotSize ?: state.lotSize, putStrikeUi?.peTimestamp)
                        }
                    )

                    HorizontalDivider(color = colors.border, thickness = 1.dp)
                }

                // ZONE 5: Tabbed Book Header (Positions / Order Book / Trade Book) & MTM Summary
                Zone5BookHeader(
                    activeTab = state.activeTab,
                    isTableView = state.isTableView,
                    positionsCount = state.positions.size,
                    ordersCount = state.orderBook.size,
                    tradesCount = state.todayTrades.size,
                    netQty = netQty,
                    totalPnlPaise = totalMtmPaise,
                    onSelectTab = { onIntent(ScalperIntent.SelectBookTab(it)) },
                    onToggleTableView = { onIntent(ScalperIntent.ToggleTableView(it)) }
                )

                // ZONE 6: Content Area (Mode 1: Mobile Cards | Mode 2: 15-Column Table | Order / Trade Books)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                ) {
                    Zone6BookContent(
                        activeTab = state.activeTab,
                        isTableView = state.isTableView,
                        positions = state.positions,
                        orderBook = state.orderBook,
                        todayTrades = state.todayTrades,
                        inFlightCommands = state.inFlightCommands,
                        onAdjustMilestone = { token, delta ->
                            onIntent(ScalperIntent.AdjustMilestoneByDelta(token, delta))
                        },
                        onToggleRatchetPause = { token, isPaused ->
                            onIntent(ScalperIntent.ToggleRatchetPause(token, isPaused))
                        },
                        onOpenEditLadder = { pos ->
                            onIntent(ScalperIntent.OpenEditLadder(pos))
                        },
                        onExitPosition = { token ->
                            onIntent(ScalperIntent.ExitPosition(token))
                        },
                        onCancelOrder = {
                            // Cancel individual order
                        }
                    )
                }
            }
        }

        // ==========================================
        // DIALOGS & BOTTOM SHEETS
        // ==========================================
        // 1. Ratchet Configuration Bottom Sheet
        if (state.isRatchetConfigOpen) {
            RatchetConfigBottomSheet(
                draft = state.ladderDraft,
                onSave = { onIntent(ScalperIntent.UpdateLadderDraft(it)) },
                onDismiss = { onIntent(ScalperIntent.OpenRatchetConfig(false)) }
            )
        }

        // 2. Flattrade browser authorization sheet
        if (state.dailyOtpSheet.isVisible) {
            DailyOtpSheet(
                sheetState = state.dailyOtpSheet,
                onFallbackBrowserClick = { onIntent(ScalperIntent.LaunchBrowserOAuthFallback) },
                onDismiss = { onIntent(ScalperIntent.DismissDailyOtpSheet) }
            )
        }

        // 3. Edit Active Ladder Bottom Sheet
        state.editingPosition?.let { pos ->
            EditLadderBottomSheet(
                position = pos,
                onDismiss = { onIntent(ScalperIntent.OpenEditLadder(null)) },
                onSave = { token, nextMilestone, stepSize, breakout, slippage ->
                    onIntent(
                        ScalperIntent.UpdateLadderConfig(
                            token = token,
                            nextMilestonePaise = nextMilestone,
                            stepSizePaise = stepSize,
                            breakoutBufferPaise = breakout,
                            slippageBufferPaise = slippage
                        )
                    )
                }
            )
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================
private fun sampleState(isDark: Boolean = false, isTable: Boolean = false): ScalperUiState {
    return ScalperUiState(isDarkTheme = isDark, isTableView = isTable)
}

@Preview(name = "Small Phone Portrait (360x780)", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
fun ScalperCockpitSmallPhonePreview() {
    ScalperCockpitScreen(
        state = sampleState(isDark = false, isTable = false),
        onIntent = {}
    )
}

@Preview(name = "Large Phone Portrait (412x915)", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ScalperCockpitLargePhonePreview() {
    ScalperCockpitScreen(
        state = sampleState(isDark = false, isTable = false),
        onIntent = {}
    )
}

@Preview(name = "Table Mode Desktop Parity (360x780)", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
fun ScalperCockpitTableModePreview() {
    ScalperCockpitScreen(
        state = sampleState(isDark = false, isTable = true),
        onIntent = {}
    )
}

@Preview(name = "Dark Theme (360x780)", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
fun ScalperCockpitDarkThemePreview() {
    ScalperCockpitScreen(
        state = sampleState(isDark = true, isTable = false),
        onIntent = {}
    )
}
