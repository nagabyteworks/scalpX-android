package com.nagabyte.scalpx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.data.remote.dto.LadderPositionDto
import com.nagabyte.scalpx.ui.scalper.ActivePositionUi
import com.nagabyte.scalpx.ui.scalper.BookTab
import com.nagabyte.scalpx.ui.scalper.OrderBookItemUi
import com.nagabyte.scalpx.ui.theme.ScalperTheme
import com.nagabyte.scalpx.ui.theme.TradingTypography
import kotlinx.collections.immutable.ImmutableList

/**
 * Zone 6: Content Area (Positions / Order Book / Trade Book).
 * Supports both Mobile Cards (Mode 1) and 15-Column Table with Pinned Symbol (Mode 2).
 */
@Composable
fun Zone6BookContent(
    activeTab: BookTab,
    isTableView: Boolean,
    positions: ImmutableList<ActivePositionUi>,
    orderBook: ImmutableList<OrderBookItemUi>,
    todayTrades: ImmutableList<LadderPositionDto>,
    inFlightCommands: Set<String>,
    onAdjustMilestone: (token: String, deltaPaise: Long) -> Unit,
    onToggleRatchetPause: (token: String, isPaused: Boolean) -> Unit,
    onOpenEditLadder: (ActivePositionUi) -> Unit,
    onExitPosition: (token: String) -> Unit,
    onCancelOrder: (orderId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors

    Box(modifier = modifier.fillMaxSize()) {
        when (activeTab) {
            BookTab.POSITIONS -> {
                if (positions.isEmpty()) {
                    EmptyPlaceholder(message = "No active positions. Execute a trade using the Buy/Sell deck above.")
                } else if (isTableView) {
                    PositionsTableView(
                        positions = positions,
                        inFlightCommands = inFlightCommands,
                        onOpenEditLadder = onOpenEditLadder,
                        onExitPosition = onExitPosition
                    )
                } else {
                    PositionsCardsView(
                        positions = positions,
                        inFlightCommands = inFlightCommands,
                        onAdjustMilestone = onAdjustMilestone,
                        onToggleRatchetPause = onToggleRatchetPause,
                        onOpenEditLadder = onOpenEditLadder,
                        onExitPosition = onExitPosition
                    )
                }
            }
            BookTab.ORDER_BOOK -> {
                if (orderBook.isEmpty()) {
                    EmptyPlaceholder(message = "No orders recorded in the current session.")
                } else {
                    OrderBookView(
                        orders = orderBook,
                        onCancelOrder = onCancelOrder
                    )
                }
            }
            BookTab.TRADE_BOOK -> {
                if (todayTrades.isEmpty()) {
                    EmptyPlaceholder(message = "No completed trades in today's session.")
                } else {
                    TradeBookView(trades = todayTrades)
                }
            }
        }
    }
}

// ==========================================
// MODE 1: MOBILE TOUCH-FRIENDLY CARDS VIEW
// ==========================================
@Composable
private fun PositionsCardsView(
    positions: ImmutableList<ActivePositionUi>,
    inFlightCommands: Set<String>,
    onAdjustMilestone: (token: String, deltaPaise: Long) -> Unit,
    onToggleRatchetPause: (token: String, isPaused: Boolean) -> Unit,
    onOpenEditLadder: (ActivePositionUi) -> Unit,
    onExitPosition: (token: String) -> Unit
) {
    val colors = ScalperTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(positions, key = { it.token }) { pos ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    // Header: Symbol, Product, Side, Net Qty
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = pos.symbol,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )

                            // Product badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colors.surfaceElevated)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = pos.product,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        // Qty Badge
                        Text(
                            text = "Net Qty: ${pos.quantity}",
                            style = TradingTypography.TabularDigitsBold,
                            fontSize = 12.sp,
                            color = colors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Price & P&L Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "LTP: ₹${MoneyFormatter.formatPaise(pos.currentLtpPaise)}",
                            style = TradingTypography.PriceDisplay,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )

                        val totalPnl = pos.unrealizedPnlPaise + pos.realizedPnlPaise
                        val isPositive = totalPnl >= 0
                        val pnlColor = if (isPositive) colors.profitGreenText else colors.lossRedText
                        val sign = if (isPositive) "+" else ""

                        Text(
                            text = "P&L: ₹$sign${MoneyFormatter.formatPaise(totalPnl)}",
                            style = TradingTypography.PnlLarge,
                            fontSize = 15.sp,
                            color = pnlColor
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sub-details: Avg, R. P&L, UR. P&L
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Avg: ₹${MoneyFormatter.formatPaise(pos.entryPricePaise)}",
                            style = TradingTypography.TabularDigits,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "R: ₹${MoneyFormatter.formatPaise(pos.realizedPnlPaise)}",
                            style = TradingTypography.TabularDigits,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "UR: ₹${MoneyFormatter.formatPaise(pos.unrealizedPnlPaise)}",
                            style = TradingTypography.TabularDigits,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = colors.border, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Ratchet SL Status & Controls Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Exchange SL Badge
                        val slTrigger = pos.exchangeSlTriggerPaise
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.exchangeSlBg)
                                .border(1.dp, colors.exchangeSlBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (slTrigger != null && slTrigger > 0) {
                                    "SL: ${MoneyFormatter.formatPaise(slTrigger)}"
                                } else {
                                    "SL: Pending"
                                },
                                style = TradingTypography.TabularDigitsBold,
                                fontSize = 10.sp,
                                color = colors.exchangeSlText
                            )
                        }

                        // Next Milestone Stepper
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Next: ${MoneyFormatter.formatPaise(pos.localNextMilestonePaise)}",
                                style = TradingTypography.TabularDigitsBold,
                                fontSize = 11.sp,
                                color = colors.textPrimary
                            )

                            // -1 pt
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colors.surfaceElevated)
                                    .border(1.dp, colors.border, RoundedCornerShape(3.dp))
                                    .clickable { onAdjustMilestone(pos.token, -100L) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "-1 pt",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            // +1 pt
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colors.surfaceElevated)
                                    .border(1.dp, colors.border, RoundedCornerShape(3.dp))
                                    .clickable { onAdjustMilestone(pos.token, 100L) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "+1 pt",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        // Actions: Pause/Resume + Edit + Exit
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Pause/Resume Ratchet
                            IconButton(
                                onClick = { onToggleRatchetPause(pos.token, !pos.ratchetPaused) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (pos.ratchetPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (pos.ratchetPaused) "Resume" else "Pause",
                                    tint = if (pos.ratchetPaused) colors.paperMode else colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Edit Ladder Config
                            IconButton(
                                onClick = { onOpenEditLadder(pos) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Edit Config",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Exit Button
                            Button(
                                onClick = { onExitPosition(pos.token) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.sellRed,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Exit",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// MODE 2: 15-COLUMN TABLE VIEW (DESKTOP PARITY)
// ==========================================
@Composable
private fun PositionsTableView(
    positions: ImmutableList<ActivePositionUi>,
    inFlightCommands: Set<String>,
    onOpenEditLadder: (ActivePositionUi) -> Unit,
    onExitPosition: (token: String) -> Unit
) {
    val colors = ScalperTheme.colors
    val horizontalScrollState = rememberScrollState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Pinned Left Column (Symbol Name)
        Column(modifier = Modifier.width(125.dp)) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(colors.surfaceElevated)
                    .border(0.5.dp, colors.border)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Symbol Name",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1
                )
            }

            // Rows
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(positions, key = { it.token }) { pos ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(0.5.dp, colors.border)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = pos.symbol,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Horizontally Scrollable 14 Columns
        Column(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScrollState)
        ) {
            // Table Header Row
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .background(colors.surfaceElevated)
                    .border(0.5.dp, colors.border),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("Product", 65.dp)
                TableHeaderCell("Side", 50.dp)
                TableHeaderCell("Net Qty", 65.dp)
                TableHeaderCell("LTP", 75.dp)
                TableHeaderCell("SL", 75.dp)
                TableHeaderCell("SL Button", 70.dp)
                TableHeaderCell("R. P&L", 80.dp)
                TableHeaderCell("UR. P&L", 80.dp)
                TableHeaderCell("P&L", 85.dp)
                TableHeaderCell("Action", 65.dp)
                TableHeaderCell("Avg Price", 80.dp)
                TableHeaderCell("Buy Qty", 70.dp)
                TableHeaderCell("Buy Price", 80.dp)
                TableHeaderCell("Sell Price", 80.dp)
                TableHeaderCell("Sell Qty", 70.dp)
            }

            // Table Data Rows
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(positions, key = { it.token }) { pos ->
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .border(0.5.dp, colors.border),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(pos.product, 65.dp)
                        TableCell(pos.side, 50.dp)
                        TableCell("${pos.quantity}", 65.dp, isBold = true)
                        TableCell(MoneyFormatter.formatPaise(pos.currentLtpPaise), 75.dp, isMonospace = true)

                        // SL
                        val slText = pos.exchangeSlTriggerPaise?.let { MoneyFormatter.formatPaise(it) } ?: "-"
                        TableCell(slText, 75.dp, isMonospace = true)

                        // SL Button
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, colors.border),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = { onOpenEditLadder(pos) },
                                modifier = Modifier.height(24.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                shape = RoundedCornerShape(3.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                            ) {
                                Text("Edit", fontSize = 9.sp, color = colors.textPrimary)
                            }
                        }

                        // Realized P&L
                        val rPnlColor = if (pos.realizedPnlPaise >= 0) colors.profitGreenText else colors.lossRedText
                        TableCell(MoneyFormatter.formatPaise(pos.realizedPnlPaise), 80.dp, color = rPnlColor, isMonospace = true)

                        // Unrealized P&L
                        val urPnlColor = if (pos.unrealizedPnlPaise >= 0) colors.profitGreenText else colors.lossRedText
                        TableCell(MoneyFormatter.formatPaise(pos.unrealizedPnlPaise), 80.dp, color = urPnlColor, isMonospace = true)

                        // Total P&L
                        val totalPnl = pos.unrealizedPnlPaise + pos.realizedPnlPaise
                        val pnlColor = if (totalPnl >= 0) colors.profitGreenText else colors.lossRedText
                        TableCell(MoneyFormatter.formatPaise(totalPnl), 85.dp, color = pnlColor, isBold = true, isMonospace = true)

                        // Action: Exit
                        Box(
                            modifier = Modifier
                                .width(65.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, colors.border),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { onExitPosition(pos.token) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.sellRed),
                                modifier = Modifier.height(24.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text("Exit", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // Avg Price
                        TableCell(MoneyFormatter.formatPaise(pos.entryPricePaise), 80.dp, isMonospace = true)

                        // Buy Qty, Buy Price
                        TableCell("${pos.buyQty}", 70.dp)
                        TableCell(if (pos.buyPricePaise > 0) MoneyFormatter.formatPaise(pos.buyPricePaise) else "-", 80.dp, isMonospace = true)

                        // Sell Price, Sell Qty
                        TableCell(if (pos.sellPricePaise > 0) MoneyFormatter.formatPaise(pos.sellPricePaise) else "-", 80.dp, isMonospace = true)
                        TableCell("${pos.sellQty}", 70.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    val colors = ScalperTheme.colors
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.5.dp, colors.border)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    color: Color = Color.Unspecified,
    isBold: Boolean = false,
    isMonospace: Boolean = false
) {
    val colors = ScalperTheme.colors
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.5.dp, colors.border)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (isMonospace) TradingTypography.TabularDigits else androidx.compose.ui.text.TextStyle.Default,
            fontSize = 10.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (color != Color.Unspecified) color else colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// ORDER BOOK VIEW
// ==========================================
@Composable
private fun OrderBookView(
    orders: ImmutableList<OrderBookItemUi>,
    onCancelOrder: (orderId: String) -> Unit
) {
    val colors = ScalperTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(orders, key = { it.orderId }) { order ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isBuy = order.side.equals("BUY", ignoreCase = true)
                            Text(
                                text = order.side,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBuy) colors.buyGreen else colors.sellRed
                            )
                            Text(
                                text = order.symbol,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = order.orderType,
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val trigText = order.triggerPricePaise?.let { " / Trig: ₹${MoneyFormatter.formatPaise(it)}" } ?: ""
                        Text(
                            text = "Qty: ${order.quantity} @ ₹${MoneyFormatter.formatPaise(order.pricePaise)}$trigText",
                            style = TradingTypography.TabularDigits,
                            fontSize = 10.sp,
                            color = colors.textSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.border, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = order.status,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        if (order.status.equals("OPEN", ignoreCase = true) || order.status.equals("TRIGGER_PENDING", ignoreCase = true)) {
                            IconButton(
                                onClick = { onCancelOrder(order.orderId) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Order",
                                    tint = colors.sellRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TRADE BOOK VIEW
// ==========================================
@Composable
private fun TradeBookView(
    trades: ImmutableList<LadderPositionDto>
) {
    val colors = ScalperTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(trades, key = { it.token }) { trade ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = trade.symbol,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Qty: ${trade.quantity} @ ₹${MoneyFormatter.formatPaise(trade.entryPricePaise)}",
                            style = TradingTypography.TabularDigits,
                            fontSize = 10.sp,
                            color = colors.textSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (trade.status == "EXITED" || trade.status == "CLOSED") colors.buyGreen.copy(alpha = 0.15f) else colors.paperMode.copy(alpha = 0.15f))
                                .border(1.dp, if (trade.status == "EXITED" || trade.status == "CLOSED") colors.buyGreen else colors.paperMode, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = trade.status,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (trade.status == "EXITED" || trade.status == "CLOSED") colors.buyGreen else colors.paperMode
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaceholder(message: String) {
    val colors = ScalperTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}
