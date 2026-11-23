package com.nagabyte.scalpx.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.ui.scalper.MessageType
import com.nagabyte.scalpx.ui.theme.ScalperTheme
import com.nagabyte.scalpx.ui.theme.TradingTypography

/**
 * Zone 3: Spot Index Ticker + Panic Actions (Close All, Cancel All) + Server Message Strip.
 */
@Composable
fun Zone3SpotPanicHub(
    selectedIndex: String,
    spotLtpPaise: Long,
    spotStatus: String,
    marketDataError: String?,
    spotChangePts: Double,
    spotChangePercent: Double,
    lastServerMessage: String,
    lastServerMessageType: MessageType,
    onCloseAllPositions: () -> Unit,
    onCancelAllOrders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Ticker & Panic Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Spot Ticker Header & LTP
            Column(
                modifier = Modifier.weight(1.3f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = if (selectedIndex == "BANKNIFTY") "NIFTY BANK" else selectedIndex,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val liveSpotAvailable = spotLtpPaise > 0L
                val formattedLtp = if (liveSpotAvailable) MoneyFormatter.formatPaise(spotLtpPaise) else "--"

                Text(
                    text = if (liveSpotAvailable) "LTP: $formattedLtp" else when (spotStatus) {
                        "STALE" -> "Spot data stale"
                        "INSTRUMENT_UNAVAILABLE" -> "Spot token unavailable"
                        "BROKER_SESSION_UNAVAILABLE" -> "Broker session expired"
                        "SUBSCRIPTION_REJECTED" -> "Subscription rejected"
                        "DISCONNECTED", "WEBSOCKET_NOT_CONNECTED" -> "Feed disconnected"
                        "UNAVAILABLE" -> "Spot unavailable"
                        else -> marketDataError ?: "Waiting for live spot"
                    },
                    style = TradingTypography.TabularDigitsBold,
                    fontSize = 11.sp,
                    color = if (liveSpotAvailable) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: Panic Action Buttons
            Row(
                modifier = Modifier.weight(1.7f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close All Positions / F6
                OutlinedButton(
                    onClick = onCloseAllPositions,
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.sellRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.sellRed)
                ) {
                    Text(
                        text = "Close All / F6",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Cancel All Orders / F7
                OutlinedButton(
                    onClick = onCancelAllOrders,
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
                ) {
                    Text(
                        text = "Cancel All / F7",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Message Strip
        val msgColor = when (lastServerMessageType) {
            MessageType.INFO -> colors.textSecondary
            MessageType.SUCCESS -> colors.profitGreenText
            MessageType.ERROR -> colors.lossRedText
        }

        Text(
            text = "Message: $lastServerMessage",
            fontSize = 10.sp,
            color = msgColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
