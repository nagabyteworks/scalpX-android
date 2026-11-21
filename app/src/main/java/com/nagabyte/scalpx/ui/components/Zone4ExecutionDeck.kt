package com.nagabyte.scalpx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nagabyte.scalpx.ui.scalper.OptionStrikeUi
import com.nagabyte.scalpx.ui.theme.ScalperTheme
import com.nagabyte.scalpx.ui.theme.TradingTypography

/**
 * Zone 4: Dual CE/PE Execution Deck (50/50 Split).
 * Touch targets >= 48dp on all trade buttons with in-flight indicators.
 */
@Composable
fun Zone4ExecutionDeck(
    callStrikeUi: OptionStrikeUi?,
    putStrikeUi: OptionStrikeUi?,
    lotSize: Int,
    inFlightCommands: Set<String>,
    onBuyCall: (token: String, symbol: String, ltpPaise: Long) -> Unit,
    onSellCall: (token: String, symbol: String, ltpPaise: Long) -> Unit,
    onBuyPut: (token: String, symbol: String, ltpPaise: Long) -> Unit,
    onSellPut: (token: String, symbol: String, ltpPaise: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left Column: Call (CE) Side
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            val ceToken = callStrikeUi?.ceToken ?: ""
            val ceSymbol = callStrikeUi?.ceSymbol ?: "CE Option"
            val ceLtpPaise = callStrikeUi?.ceLtpPaise ?: 0L
            val isCeInFlight = inFlightCommands.any { it.contains(ceToken) }

            Text(
                text = "Strike: $ceSymbol",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "LTP: ${if (ceLtpPaise > 0) MoneyFormatter.formatPaise(ceLtpPaise) else "--"}",
                style = TradingTypography.PriceDisplay,
                fontSize = 16.sp,
                color = colors.textPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Dual Buttons: Sell Call (Red) & Buy Call (Green)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Sell Call Button
                Button(
                    onClick = {
                        if (ceToken.isNotBlank() && !isCeInFlight) {
                            onSellCall(ceToken, ceSymbol, ceLtpPaise)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.sellRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    enabled = ceToken.isNotBlank() && !isCeInFlight
                ) {
                    if (isCeInFlight) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "← Sell Call",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Buy Call Button
                Button(
                    onClick = {
                        if (ceToken.isNotBlank() && !isCeInFlight) {
                            onBuyCall(ceToken, ceSymbol, ceLtpPaise)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.buyGreen,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    enabled = ceToken.isNotBlank() && !isCeInFlight
                ) {
                    if (isCeInFlight) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "↑ Buy Call",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Use buttons or shortcuts to trade Call",
                fontSize = 9.sp,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right Column: Put (PE) Side
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            val peToken = putStrikeUi?.peToken ?: ""
            val peSymbol = putStrikeUi?.peSymbol ?: "PE Option"
            val peLtpPaise = putStrikeUi?.peLtpPaise ?: 0L
            val isPeInFlight = inFlightCommands.any { it.contains(peToken) }

            Text(
                text = "Strike: $peSymbol",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )

            Text(
                text = "${if (peLtpPaise > 0) MoneyFormatter.formatPaise(peLtpPaise) else "--"} :LTP",
                style = TradingTypography.PriceDisplay,
                fontSize = 16.sp,
                color = colors.textPrimary,
                maxLines = 1,
                textAlign = TextAlign.End
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Dual Buttons: Buy Put (Green) & Sell Put (Red)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Buy Put Button
                Button(
                    onClick = {
                        if (peToken.isNotBlank() && !isPeInFlight) {
                            onBuyPut(peToken, peSymbol, peLtpPaise)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.buyGreen,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    enabled = peToken.isNotBlank() && !isPeInFlight
                ) {
                    if (isPeInFlight) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "↓ Buy Put",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Sell Put Button
                Button(
                    onClick = {
                        if (peToken.isNotBlank() && !isPeInFlight) {
                            onSellPut(peToken, peSymbol, peLtpPaise)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.sellRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    enabled = peToken.isNotBlank() && !isPeInFlight
                ) {
                    if (isPeInFlight) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "→ Sell Put",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Use buttons or shortcuts to trade Put",
                fontSize = 9.sp,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
    }
}
