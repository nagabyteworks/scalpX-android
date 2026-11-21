package com.nagabyte.scalpx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.ui.scalper.LadderDraftUi
import com.nagabyte.scalpx.ui.scalper.OptionStrikeUi
import com.nagabyte.scalpx.ui.theme.TradingColors
import com.nagabyte.scalpx.ui.theme.TradingTypography
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun QuickExecutionPanel(
    selectedIndex: String,
    selectedExpiry: String,
    availableExpiries: ImmutableList<String>,
    product: String,
    strikes: ImmutableList<OptionStrikeUi>,
    selectedStrike: Double?,
    selectedLots: Int,
    lotSize: Int,
    ladderDraft: LadderDraftUi,
    inFlightCommands: Set<String>,
    brokerConnected: Boolean,
    modifier: Modifier = Modifier,
    onSelectIndex: (String) -> Unit = {},
    onSelectExpiry: (String) -> Unit = {},
    onSelectProduct: (String) -> Unit = {},
    onSelectStrike: (Double) -> Unit = {},
    onSelectLots: (Int) -> Unit = {},
    onUpdateDraft: (LadderDraftUi) -> Unit = {},
    onOneClickBuy: (token: String, symbol: String, ltpPaise: Long, lotSize: Int) -> Unit = { _, _, _, _ -> }
) {
    var isExpiryMenuExpanded by remember { mutableStateOf(false) }
    var isRatchetExpanded by remember { mutableStateOf(false) }

    val activeStrikeObj = strikes.find { it.strikePrice == selectedStrike }
        ?: strikes.getOrNull(strikes.size / 2)

    val ceToken = activeStrikeObj?.ceToken
    val ceSymbol = activeStrikeObj?.ceSymbol ?: "${selectedIndex} CE"
    val ceLtp = activeStrikeObj?.ceLtpPaise ?: 0L

    val peToken = activeStrikeObj?.peToken
    val peSymbol = activeStrikeObj?.peSymbol ?: "${selectedIndex} PE"
    val peLtp = activeStrikeObj?.peLtpPaise ?: 0L

    val isCeInFlight = ceToken != null && (inFlightCommands.contains("BUY_$ceToken") || inFlightCommands.contains("BUY_CE_$ceToken"))
    val isPeInFlight = peToken != null && (inFlightCommands.contains("BUY_$peToken") || inFlightCommands.contains("BUY_CE_$peToken"))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TradingColors.SurfaceCard)
            .border(1.dp, TradingColors.Border, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Index Selector + Expiry Dropdown + MIS/NRML Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index Segmented Chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("NIFTY", "BANKNIFTY", "SENSEX").forEach { idx ->
                    val isSelected = selectedIndex.equals(idx, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectIndex(idx) },
                        color = if (isSelected) TradingColors.SurfaceElevated else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, TradingColors.ProfitGreen) else BorderStroke(1.dp, TradingColors.Border),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = idx,
                            color = if (isSelected) TradingColors.ProfitGreen else TradingColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Expiry Dropdown + Product Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Expiry Selector
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isExpiryMenuExpanded = true },
                        color = TradingColors.SurfaceElevated,
                        border = BorderStroke(1.dp, TradingColors.Border),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedExpiry.ifBlank { "EXPIRY" },
                                color = TradingColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = TradingColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isExpiryMenuExpanded,
                        onDismissRequest = { isExpiryMenuExpanded = false },
                        modifier = Modifier.background(TradingColors.SurfaceElevated)
                    ) {
                        if (availableExpiries.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(selectedExpiry.ifBlank { "Current Expiry" }, color = TradingColors.TextPrimary) },
                                onClick = { isExpiryMenuExpanded = false }
                            )
                        } else {
                            availableExpiries.forEach { exp ->
                                DropdownMenuItem(
                                    text = { Text(exp, color = TradingColors.TextPrimary) },
                                    onClick = {
                                        onSelectExpiry(exp)
                                        isExpiryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // MIS / NRML Toggle Chip
                val isMis = product.equals("MIS", ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectProduct(if (isMis) "NRML" else "MIS") },
                    color = TradingColors.SurfaceElevated,
                    border = BorderStroke(1.dp, TradingColors.Border),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isMis) "MIS" else "NRML",
                        color = if (isMis) TradingColors.WarningAmber else TradingColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 2. Horizontal ATM-Centered Strike Strip
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "SELECT STRIKE (ATM STRIP)",
                color = TradingColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            if (strikes.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    color = TradingColors.SurfaceElevated,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Loading option chain...",
                            color = TradingColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(strikes) { item ->
                        val isSelected = selectedStrike != null && selectedStrike == item.strikePrice
                        val ceLtpText = item.ceLtpPaise?.let { MoneyFormatter.formatPaise(it, false) } ?: "-"
                        val peLtpText = item.peLtpPaise?.let { MoneyFormatter.formatPaise(it, false) } ?: "-"

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectStrike(item.strikePrice) },
                            color = if (isSelected) TradingColors.SurfaceElevated else Color(0xFF10141B),
                            border = if (isSelected) BorderStroke(1.5.dp, TradingColors.ProfitGreen) else BorderStroke(1.dp, TradingColors.Border),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = ceLtpText,
                                    style = TradingTypography.TabularDigits,
                                    color = TradingColors.BuyCe,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.strikePrice.toInt().toString(),
                                    style = TradingTypography.TabularDigitsBold,
                                    color = TradingColors.TextPrimary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = peLtpText,
                                    style = TradingTypography.TabularDigits,
                                    color = TradingColors.BuyPe,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Preset Lots Selector + Collapsible Ratchet Defaults Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lot Preset Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Lots:",
                    color = TradingColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                listOf(1, 2, 5, 10).forEach { lot ->
                    val isSelected = selectedLots == lot
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onSelectLots(lot) },
                        color = if (isSelected) TradingColors.ProfitGreen else TradingColors.SurfaceElevated,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "$lot",
                            color = if (isSelected) Color.Black else TradingColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "(${selectedLots * lotSize} qty)",
                    style = TradingTypography.TabularDigits,
                    color = TradingColors.TextMuted,
                    fontSize = 11.sp
                )
            }

            // Ratchet Defaults Toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { isRatchetExpanded = !isRatchetExpanded }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+${ladderDraft.firstTargetPts.toInt()}/+${ladderDraft.stepPts.toInt()} SL",
                    style = TradingTypography.TabularDigitsBold,
                    color = TradingColors.WarningAmber,
                    fontSize = 11.sp
                )
                Icon(
                    imageVector = if (isRatchetExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TradingColors.WarningAmber,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Expandable Ratchet Defaults Detail Row
        AnimatedVisibility(visible = isRatchetExpanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = TradingColors.SurfaceElevated,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, TradingColors.Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RatchetTweakItem(
                        label = "+Target",
                        value = "${ladderDraft.firstTargetPts.toInt()} pt",
                        onDecrement = { onUpdateDraft(ladderDraft.copy(firstTargetPts = (ladderDraft.firstTargetPts - 1.0).coerceAtLeast(1.0))) },
                        onIncrement = { onUpdateDraft(ladderDraft.copy(firstTargetPts = ladderDraft.firstTargetPts + 1.0)) }
                    )
                    RatchetTweakItem(
                        label = "+Step",
                        value = "${ladderDraft.stepPts.toInt()} pt",
                        onDecrement = { onUpdateDraft(ladderDraft.copy(stepPts = (ladderDraft.stepPts - 1.0).coerceAtLeast(1.0))) },
                        onIncrement = { onUpdateDraft(ladderDraft.copy(stepPts = ladderDraft.stepPts + 1.0)) }
                    )
                    RatchetTweakItem(
                        label = "Breakout",
                        value = "${ladderDraft.breakoutBufferPts} pt",
                        onDecrement = { onUpdateDraft(ladderDraft.copy(breakoutBufferPts = (ladderDraft.breakoutBufferPts - 0.5).coerceAtLeast(0.5))) },
                        onIncrement = { onUpdateDraft(ladderDraft.copy(breakoutBufferPts = ladderDraft.breakoutBufferPts + 0.5)) }
                    )
                    RatchetTweakItem(
                        label = "Init SL",
                        value = "${ladderDraft.initialSlPts.toInt()} pt",
                        onDecrement = { onUpdateDraft(ladderDraft.copy(initialSlPts = (ladderDraft.initialSlPts - 1.0).coerceAtLeast(1.0))) },
                        onIncrement = { onUpdateDraft(ladderDraft.copy(initialSlPts = ladderDraft.initialSlPts + 1.0)) }
                    )
                }
            }
        }

        // 4. Large Thumb-Friendly 1-Click BUY CE & BUY PE Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // BUY CE Button
            Button(
                onClick = {
                    if (ceToken != null) {
                        onOneClickBuy(ceToken, ceSymbol, ceLtp, lotSize)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TradingColors.BuyCe,
                    disabledContainerColor = TradingColors.BuyCe.copy(alpha = 0.4f)
                ),
                enabled = ceToken != null && !isCeInFlight && brokerConnected
            ) {
                if (isCeInFlight) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "1-CLICK BUY CE",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${selectedLots * lotSize} qty @ ${MoneyFormatter.formatPaise(ceLtp)}",
                            style = TradingTypography.TabularDigits,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // BUY PE Button
            Button(
                onClick = {
                    if (peToken != null) {
                        onOneClickBuy(peToken, peSymbol, peLtp, lotSize)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TradingColors.BuyPe,
                    disabledContainerColor = TradingColors.BuyPe.copy(alpha = 0.4f)
                ),
                enabled = peToken != null && !isPeInFlight && brokerConnected
            ) {
                if (isPeInFlight) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "1-CLICK BUY PE",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${selectedLots * lotSize} qty @ ${MoneyFormatter.formatPaise(peLtp)}",
                            style = TradingTypography.TabularDigits,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatchetTweakItem(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = TradingColors.TextMuted, fontSize = 9.sp)
        Text(text = value, style = TradingTypography.TabularDigitsBold, color = TradingColors.TextPrimary, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onDecrement() },
                color = TradingColors.Border
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("-", color = TradingColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Surface(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onIncrement() },
                color = TradingColors.Border
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+", color = TradingColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Preview
@Composable
fun QuickExecutionPanelPreview() {
    QuickExecutionPanel(
        selectedIndex = "NIFTY",
        selectedExpiry = "28NOV24",
        availableExpiries = persistentListOf("28NOV24", "05DEC24"),
        product = "MIS",
        strikes = persistentListOf(
            OptionStrikeUi(24400.0, "1", "NIFTY24400CE", 14500L, "2", "NIFTY24400PE", 8500L),
            OptionStrikeUi(24500.0, "3", "NIFTY24500CE", 9500L, "4", "NIFTY24500PE", 12000L),
            OptionStrikeUi(24600.0, "5", "NIFTY24600CE", 6000L, "6", "NIFTY24600PE", 17500L)
        ),
        selectedStrike = 24500.0,
        selectedLots = 2,
        lotSize = 25,
        ladderDraft = LadderDraftUi(),
        inFlightCommands = emptySet(),
        brokerConnected = true
    )
}
