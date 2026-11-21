package com.nagabyte.scalpx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.ui.auth.DailyOtpSheet
import com.nagabyte.scalpx.ui.components.*
import com.nagabyte.scalpx.ui.scalper.*
import com.nagabyte.scalpx.ui.theme.LocalScalperTheme
import com.nagabyte.scalpx.ui.theme.ScalperTheme
import com.nagabyte.scalpx.ui.theme.getScalperColors
import kotlinx.collections.immutable.persistentListOf

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

        var localConfirmation by remember { mutableStateOf<PendingOrderConfirmation?>(null) }

        // Local helper for buy/sell actions with safety check
        fun executeOrConfirm(
            side: String,
            token: String,
            symbol: String,
            ltpPaise: Long,
            lotSize: Int
        ) {
            if (state.oneClickEnabled) {
                if (side == "BUY") {
                    onIntent(ScalperIntent.OneClickBuy(token, symbol, ltpPaise, lotSize))
                } else {
                    onIntent(ScalperIntent.OneClickSell(token, symbol, ltpPaise, lotSize))
                }
            } else {
                val actionText = if (side == "BUY") "Buy" else "Sell"
                val lotsText = "${state.selectedLots} Lots (${state.selectedLots * lotSize})"
                val priceText = if (ltpPaise > 0) " at ~₹${MoneyFormatter.formatPaise(ltpPaise)}" else ""
                localConfirmation = PendingOrderConfirmation(
                    title = "Confirm $actionText Order",
                    message = "Are you sure you want to $actionText $lotsText $symbol$priceText?",
                    onConfirm = {
                        if (side == "BUY") {
                            onIntent(ScalperIntent.OneClickBuy(token, symbol, ltpPaise, lotSize))
                        } else {
                            onIntent(ScalperIntent.OneClickSell(token, symbol, ltpPaise, lotSize))
                        }
                    }
                )
            }
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                // ZONE 1: Pinned Top Header Bar (~44dp)
                Zone1BrokerHeader(
                    brokerMode = state.brokerMode,
                    serverWsState = state.serverWsState,
                    brokerConnected = state.brokerConnected,
                    wsFeedHealthy = state.wsFeedHealthy,
                    pingMs = state.pingMs,
                    userId = state.userId,
                    isDarkTheme = state.isDarkTheme,
                    onConnectOtp = { onIntent(ScalperIntent.OpenDailyOtpSheet) },
                    onToggleTheme = { onIntent(ScalperIntent.ToggleTheme(it)) },
                    onOpenSettings = { onIntent(ScalperIntent.OpenSettings(true)) }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(colors.background)
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
                    // ZONE 2: 2-Row Parameter Selector Grid + Safety Toggle & Ratchet SL Pill
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
                        oneClickEnabled = state.oneClickEnabled,
                        isCollapsed = state.isZone2Collapsed,
                        onSelectIndex = { onIntent(ScalperIntent.SelectIndex(it)) },
                        onSelectExpiry = { onIntent(ScalperIntent.SelectExpiry(it)) },
                        onSelectProduct = { onIntent(ScalperIntent.SelectProduct(it)) },
                        onStepCallStrike = { delta -> onIntent(ScalperIntent.StepStrike(isCall = true, deltaSteps = delta)) },
                        onStepPutStrike = { delta -> onIntent(ScalperIntent.StepStrike(isCall = false, deltaSteps = delta)) },
                        onStepLots = { delta -> onIntent(ScalperIntent.StepLots(delta)) },
                        onToggleOneClick = { onIntent(ScalperIntent.ToggleOneClick(it)) },
                        onToggleCollapse = { onIntent(ScalperIntent.ToggleZone2Collapse(it)) },
                        onOpenRatchetConfig = { onIntent(ScalperIntent.OpenRatchetConfig(true)) }
                    )

                    HorizontalDivider(color = colors.border, thickness = 1.dp)

                    // ZONE 3: Spot Index Ticker + Panic Actions + Server Message Strip
                    Zone3SpotPanicHub(
                        selectedIndex = state.selectedIndex,
                        spotLtpPaise = state.spotLtpPaise,
                        spotChangePts = state.spotChangePts,
                        spotChangePercent = state.spotChangePercent,
                        lastServerMessage = state.lastServerMessage,
                        lastServerMessageType = state.lastServerMessageType,
                        onCloseAllPositions = { onIntent(ScalperIntent.PanicExitAll) },
                        onCancelAllOrders = { onIntent(ScalperIntent.CancelAllOrders) }
                    )

                    HorizontalDivider(color = colors.border, thickness = 1.dp)

                    // ZONE 4: Dual CE/PE Execution Deck (50/50 Dual Deck, Touch Targets >= 48dp)
                    Zone4ExecutionDeck(
                        callStrikeUi = callStrikeUi,
                        putStrikeUi = putStrikeUi,
                        lotSize = state.lotSize,
                        inFlightCommands = state.inFlightCommands,
                        onBuyCall = { token, symbol, ltp ->
                            executeOrConfirm("BUY", token, symbol, ltp, state.lotSize)
                        },
                        onSellCall = { token, symbol, ltp ->
                            executeOrConfirm("SELL", token, symbol, ltp, state.lotSize)
                        },
                        onBuyPut = { token, symbol, ltp ->
                            executeOrConfirm("BUY", token, symbol, ltp, state.lotSize)
                        },
                        onSellPut = { token, symbol, ltp ->
                            executeOrConfirm("SELL", token, symbol, ltp, state.lotSize)
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
                Box(modifier = Modifier.weight(1f)) {
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
        // 1. One-Click Order Confirmation Dialog
        (localConfirmation ?: state.pendingConfirmation)?.let { confirmation ->
            OrderConfirmationDialog(
                confirmation = confirmation,
                onDismiss = {
                    localConfirmation = null
                    onIntent(ScalperIntent.DismissConfirmation)
                }
            )
        }

        // 2. Ratchet Configuration Bottom Sheet
        if (state.isRatchetConfigOpen) {
            RatchetConfigBottomSheet(
                draft = state.ladderDraft,
                onSave = { onIntent(ScalperIntent.UpdateLadderDraft(it)) },
                onDismiss = { onIntent(ScalperIntent.OpenRatchetConfig(false)) }
            )
        }

        // 3. Daily Flattrade OTP Sheet
        if (state.dailyOtpSheet.isVisible) {
            DailyOtpSheet(
                sheetState = state.dailyOtpSheet,
                brokerMode = state.brokerMode,
                onOtpDigitsChanged = { onIntent(ScalperIntent.UpdateOtpDigits(it)) },
                onSubmit = { onIntent(ScalperIntent.SubmitDailyOtp) },
                onFallbackBrowserClick = { onIntent(ScalperIntent.LaunchBrowserOAuthFallback) },
                onDismiss = { onIntent(ScalperIntent.DismissDailyOtpSheet) }
            )
        }

        // 4. Edit Active Ladder Bottom Sheet
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
    val samplePositions = persistentListOf(
        ActivePositionUi(
            token = "42300CE",
            symbol = "BANKNIFTY22D2242300CE",
            quantity = 0,
            entryPricePaise = 30900L,
            currentLtpPaise = 28680L,
            unrealizedPnlPaise = 0L,
            realizedPnlPaise = -140250L,
            exchangeSlTriggerPaise = 0L,
            localNextMilestonePaise = 32000L,
            stepSizePaise = 500L,
            product = "MARGIN",
            side = "-",
            buyQty = 1275,
            buyPricePaise = 31000L,
            sellQty = 1275,
            sellPricePaise = 30900L
        ),
        ActivePositionUi(
            token = "43000PE",
            symbol = "BANKNIFTY22D2243000PE",
            quantity = 0,
            entryPricePaise = 34400L,
            currentLtpPaise = 49265L,
            unrealizedPnlPaise = 0L,
            realizedPnlPaise = 830625L,
            exchangeSlTriggerPaise = 0L,
            localNextMilestonePaise = 51000L,
            stepSizePaise = 500L,
            product = "MARGIN",
            side = "-",
            buyQty = 375,
            buyPricePaise = 32200L,
            sellQty = 375,
            sellPricePaise = 34400L
        ),
        ActivePositionUi(
            token = "43100PE",
            symbol = "BANKNIFTY22D2243100PE",
            quantity = 0,
            entryPricePaise = 33200L,
            currentLtpPaise = 58555L,
            unrealizedPnlPaise = 0L,
            realizedPnlPaise = 1090475L,
            exchangeSlTriggerPaise = 0L,
            localNextMilestonePaise = 60000L,
            stepSizePaise = 500L,
            product = "MARGIN",
            side = "-",
            buyQty = 1325,
            buyPricePaise = 32400L,
            sellQty = 1325,
            sellPricePaise = 33200L
        )
    )

    val sampleStrikes = persistentListOf(
        OptionStrikeUi(
            strikePrice = 42300.0,
            ceToken = "42300CE",
            ceSymbol = "BANKNIFTY22122242300CE",
            ceLtpPaise = 28680L,
            peToken = "42300PE",
            peSymbol = "BANKNIFTY22122242300PE",
            peLtpPaise = 18050L
        ),
        OptionStrikeUi(
            strikePrice = 42700.0,
            ceToken = "42700CE",
            ceSymbol = "BANKNIFTY22122242700CE",
            ceLtpPaise = 14500L,
            peToken = "42700PE",
            peSymbol = "BANKNIFTY22122242700PE",
            peLtpPaise = 23490L
        )
    )

    return ScalperUiState(
        brokerMode = "LIVE",
        brokerConnected = true,
        wsFeedHealthy = true,
        pingMs = 24L,
        userId = "FT01234",
        selectedIndex = "BANKNIFTY",
        selectedExpiry = "22-Dec-2022",
        selectedCallStrike = 42300.0,
        selectedPutStrike = 42700.0,
        selectedLots = 1,
        lotSize = 15,
        product = "MIS",
        spotLtpPaise = 4252080L,
        spotChangePts = -97.15,
        spotChangePercent = -0.23,
        oneClickEnabled = true,
        isDarkTheme = isDark,
        isTableView = isTable,
        lastServerMessage = "-",
        totalRealizedPnlPaise = 1780850L,
        totalUnrealizedPnlPaise = 0L,
        strikes = sampleStrikes,
        positions = samplePositions
    )
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
