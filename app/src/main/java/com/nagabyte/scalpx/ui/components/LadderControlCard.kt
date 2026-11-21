package com.nagabyte.scalpx.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.ui.scalper.ActivePositionUi
import com.nagabyte.scalpx.ui.theme.TradingColors
import com.nagabyte.scalpx.ui.theme.TradingTypography

@Composable
fun LadderControlCard(
    position: ActivePositionUi,
    inFlightCommands: Set<String>,
    modifier: Modifier = Modifier,
    onAdjustMilestone: (token: String, deltaPaise: Long) -> Unit = { _, _ -> },
    onToggleRatchetPause: (token: String, isPaused: Boolean) -> Unit = { _, _ -> },
    onOpenEditLadder: (ActivePositionUi) -> Unit = {},
    onExitPosition: (token: String) -> Unit = {}
) {
    val isExitInFlight = inFlightCommands.contains("EXIT_${position.token}")
    val isAdjustInFlight = inFlightCommands.contains("ADJUST_${position.token}")
    val isPauseInFlight = inFlightCommands.contains("PAUSE_${position.token}")

    val pnlColor = if (position.unrealizedPnlPaise >= 0) TradingColors.ProfitGreen else TradingColors.LossRed
    val pnlSign = if (position.unrealizedPnlPaise >= 0) "+" else ""

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TradingColors.Border, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = TradingColors.SurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Header: Symbol, Net Qty, Live LTP, Bold Unrealized MTM P&L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = position.symbol,
                            color = TradingColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Surface(
                            color = if (position.quantity > 0) TradingColors.ProfitGreen.copy(alpha = 0.2f) else TradingColors.LossRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (position.quantity > 0) "+${position.quantity}" else "${position.quantity}",
                                color = if (position.quantity > 0) TradingColors.ProfitGreen else TradingColors.LossRed,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Entry: ${MoneyFormatter.formatPaise(position.entryPricePaise)} | LTP: ${MoneyFormatter.formatPaise(position.currentLtpPaise)}",
                        style = TradingTypography.TabularDigits,
                        color = TradingColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$pnlSign${MoneyFormatter.formatPaise(position.unrealizedPnlPaise)}",
                        style = TradingTypography.PnlLarge,
                        color = pnlColor
                    )
                    Text(
                        text = "UNREALIZED P&L",
                        color = TradingColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. Two Visually Unmistakable Badges Side-by-Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Badge 1: Solid Filled Badge (Exchange SL Protected on NSE)
                Surface(
                    modifier = Modifier.weight(1f),
                    color = TradingColors.ExchangeSlBg,
                    border = BorderStroke(1.dp, TradingColors.ExchangeSlBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "EXCHANGE SL (NSE)",
                            color = TradingColors.ExchangeSlText.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        val slTrigText = position.exchangeSlTriggerPaise?.let { MoneyFormatter.formatPaise(it) } ?: "Pending"
                        val slLmtText = position.exchangeSlLimitPaise?.let { "Lmt ${MoneyFormatter.formatPaise(it)}" } ?: "Calculating"
                        Text(
                            text = slTrigText,
                            style = TradingTypography.TabularDigitsBold,
                            color = TradingColors.ExchangeSlText,
                            fontSize = 13.sp
                        )
                        Text(
                            text = slLmtText,
                            style = TradingTypography.TabularDigits,
                            color = TradingColors.ExchangeSlText.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }

                // Badge 2: Dashed-Border Outline Badge (Local Trigger Ratchet)
                Surface(
                    modifier = Modifier.weight(1f),
                    color = TradingColors.LocalTriggerBg,
                    border = BorderStroke(1.dp, TradingColors.LocalTriggerBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "LOCAL TRIGGER (RATCHET)",
                            color = TradingColors.LocalTriggerText.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        Text(
                            text = MoneyFormatter.formatPaise(position.localNextMilestonePaise),
                            style = TradingTypography.TabularDigitsBold,
                            color = TradingColors.LocalTriggerText,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (position.ratchetPaused) "PAUSED" else "(+${position.stepSizePaise / 100} pt step)",
                            style = TradingTypography.TabularDigits,
                            color = if (position.ratchetPaused) TradingColors.LossRed else TradingColors.LocalTriggerText.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = if (position.ratchetPaused) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // 3. Live Mid-Trade Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick -1 and +1 Step Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !isAdjustInFlight) {
                                onAdjustMilestone(position.token, -100L) // -1 pt
                            },
                        color = TradingColors.SurfaceElevated,
                        border = BorderStroke(1.dp, TradingColors.Border),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "-1 pt",
                            style = TradingTypography.TabularDigitsBold,
                            color = TradingColors.TextPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !isAdjustInFlight) {
                                onAdjustMilestone(position.token, 100L) // +1 pt
                            },
                        color = TradingColors.SurfaceElevated,
                        border = BorderStroke(1.dp, TradingColors.Border),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "+1 pt",
                            style = TradingTypography.TabularDigitsBold,
                            color = TradingColors.TextPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }

                // Pause / Resume Toggle
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = !isPauseInFlight) {
                            onToggleRatchetPause(position.token, !position.ratchetPaused)
                        },
                    color = if (position.ratchetPaused) TradingColors.WarningAmber.copy(alpha = 0.2f) else TradingColors.SurfaceElevated,
                    border = BorderStroke(1.dp, if (position.ratchetPaused) TradingColors.WarningAmber else TradingColors.Border),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (position.ratchetPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (position.ratchetPaused) TradingColors.WarningAmber else TradingColors.TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (position.ratchetPaused) "Resume" else "Pause",
                            color = if (position.ratchetPaused) TradingColors.WarningAmber else TradingColors.TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }

                // Edit Ladder Parameters Icon Button
                IconButton(
                    onClick = { onOpenEditLadder(position) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Ladder Parameters",
                        tint = TradingColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Single-Tap Red EXIT Button
                Button(
                    onClick = { onExitPosition(position.token) },
                    colors = ButtonDefaults.buttonColors(containerColor = TradingColors.LossRed),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp),
                    enabled = !isExitInFlight
                ) {
                    if (isExitInFlight) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "EXIT",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun LadderControlCardPreview() {
    LadderControlCard(
        position = ActivePositionUi(
            token = "26000",
            symbol = "NIFTY24DEC24500CE",
            quantity = 50,
            entryPricePaise = 10000L,
            currentLtpPaise = 11250L,
            unrealizedPnlPaise = 62500L,
            exchangeSlTriggerPaise = 11000L,
            exchangeSlLimitPaise = 10850L,
            localNextMilestonePaise = 11500L,
            stepSizePaise = 500L,
            ratchetPaused = false
        ),
        inFlightCommands = emptySet()
    )
}
