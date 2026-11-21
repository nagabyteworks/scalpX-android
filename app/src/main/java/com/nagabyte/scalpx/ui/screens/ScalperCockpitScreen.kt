package com.nagabyte.scalpx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.ui.components.*
import com.nagabyte.scalpx.ui.scalper.ScalperIntent
import com.nagabyte.scalpx.ui.scalper.ScalperUiState
import com.nagabyte.scalpx.ui.theme.TradingColors

@Composable
fun ScalperCockpitScreen(
    state: ScalperUiState,
    onIntent: (ScalperIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TradingColors.Background,
        topBar = {
            ConnectionBanner(
                brokerMode = state.brokerMode,
                serverWsState = state.serverWsState,
                brokerConnected = state.brokerConnected,
                wsFeedHealthy = state.wsFeedHealthy,
                pingMs = state.pingMs,
                onConnectFlattrade = { onIntent(ScalperIntent.ConnectFlattrade) },
                onOpenTodayTrades = { onIntent(ScalperIntent.OpenTodayTrades) },
                onOpenSettings = { onIntent(ScalperIntent.OpenSettings(true)) }
            )
        },
        bottomBar = {
            PanicExitBar(
                totalUnrealizedPnlPaise = state.totalUnrealizedPnlPaise,
                totalRealizedPnlPaise = state.totalRealizedPnlPaise,
                inFlightCommands = state.inFlightCommands,
                onPanicExitAll = { onIntent(ScalperIntent.PanicExitAll) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)
        ) {
            // Emergency Manual Intervention Banner
            state.criticalAlert?.let { alert ->
                item(key = "critical_alert") {
                    ManualInterventionBanner(
                        alert = alert,
                        onDismiss = { onIntent(ScalperIntent.DismissAlert) }
                    )
                }
            }

            // Quick Execution Cockpit Panel (Top Half)
            item(key = "quick_execution_panel") {
                QuickExecutionPanel(
                    selectedIndex = state.selectedIndex,
                    selectedExpiry = state.selectedExpiry,
                    availableExpiries = state.availableExpiries,
                    product = state.product,
                    strikes = state.strikes,
                    selectedStrike = state.selectedStrike,
                    selectedLots = state.selectedLots,
                    lotSize = state.lotSize,
                    ladderDraft = state.ladderDraft,
                    inFlightCommands = state.inFlightCommands,
                    brokerConnected = state.brokerConnected,
                    onSelectIndex = { onIntent(ScalperIntent.SelectIndex(it)) },
                    onSelectExpiry = { onIntent(ScalperIntent.SelectExpiry(it)) },
                    onSelectProduct = { onIntent(ScalperIntent.SelectProduct(it)) },
                    onSelectStrike = { onIntent(ScalperIntent.SelectStrike(it)) },
                    onSelectLots = { onIntent(ScalperIntent.SelectLots(it)) },
                    onUpdateDraft = { onIntent(ScalperIntent.UpdateLadderDraft(it)) },
                    onOneClickBuy = { token, symbol, ltpPaise, lotSize ->
                        onIntent(ScalperIntent.OneClickBuy(token, symbol, ltpPaise, lotSize))
                    }
                )
            }

            // Active Positions Section Header
            item(key = "positions_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE LADDERS (${state.positions.size})",
                        color = TradingColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Active Positions List
            if (state.positions.isEmpty()) {
                item(key = "empty_positions") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TradingColors.SurfaceCard),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No active ladders. Tap 1-CLICK BUY CE or PE above to execute.",
                                color = TradingColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(state.positions, key = { it.token }) { position ->
                    LadderControlCard(
                        position = position,
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
                        }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheets
    if (state.isTodayTradesOpen) {
        TodayTradesBottomSheet(
            trades = state.todayTrades,
            totalRealizedPnlPaise = state.totalRealizedPnlPaise,
            onDismiss = { onIntent(ScalperIntent.CloseTodayTrades) }
        )
    }

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

@Preview
@Composable
fun ScalperCockpitScreenPreview() {
    ScalperCockpitScreen(
        state = ScalperUiState(),
        onIntent = {}
    )
}
