package com.nagabyte.scalpx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.ui.scalper.WsState
import com.nagabyte.scalpx.ui.theme.TradingColors
import com.nagabyte.scalpx.ui.theme.TradingTypography

@Composable
fun ConnectionBanner(
    brokerMode: String,
    serverWsState: WsState,
    brokerConnected: Boolean,
    wsFeedHealthy: Boolean,
    pingMs: Long,
    modifier: Modifier = Modifier,
    onConnectFlattrade: () -> Unit = {},
    onOpenTodayTrades: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TradingColors.SurfaceCard)
            .border(width = 1.dp, color = TradingColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Group: Mode Pill + WS / Feed Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // PAPER / LIVE Mode Pill
                val isLive = brokerMode.equals("LIVE", ignoreCase = true)
                Surface(
                    color = if (isLive) TradingColors.LiveMode else TradingColors.PaperMode,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isLive) "LIVE" else "PAPER",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Server WS Pill
                val wsColor = when (serverWsState) {
                    WsState.CONNECTED -> TradingColors.ProfitGreen
                    WsState.CONNECTING -> TradingColors.WarningAmber
                    WsState.DISCONNECTED, WsState.AUTH_ERROR -> TradingColors.LossRed
                }
                Surface(
                    color = TradingColors.SurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TradingColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(wsColor)
                        )
                        Text(
                            text = if (serverWsState == WsState.CONNECTED) "${pingMs}ms" else serverWsState.name,
                            style = TradingTypography.TabularDigits,
                            color = TradingColors.TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Flattrade Broker Feed Pill
                val feedColor = if (brokerConnected && wsFeedHealthy) TradingColors.ProfitGreen else TradingColors.WarningAmber
                Surface(
                    color = TradingColors.SurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TradingColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(feedColor)
                        )
                        Text(
                            text = if (brokerConnected) "FEED" else "BROKER OFF",
                            color = TradingColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Right Group: Connect Button / Action Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!brokerConnected) {
                    Button(
                        onClick = onConnectFlattrade,
                        colors = ButtonDefaults.buttonColors(containerColor = TradingColors.WarningAmber),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "Connect Flattrade",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onOpenTodayTrades,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Today's Trades",
                        tint = TradingColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TradingColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Stale Feed Animated Warning Strip
        AnimatedVisibility(visible = !wsFeedHealthy && brokerConnected) {
            val infiniteTransition = rememberInfiniteTransition(label = "stale_pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TradingColors.WarningAmber.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .alpha(alpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = TradingColors.WarningAmber,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FEED STALE - RECONNECTING TO MARKET STREAM",
                    color = TradingColors.WarningAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Preview
@Composable
fun ConnectionBannerPreview() {
    ConnectionBanner(
        brokerMode = "PAPER",
        serverWsState = WsState.CONNECTED,
        brokerConnected = true,
        wsFeedHealthy = true,
        pingMs = 14L
    )
}

@Preview
@Composable
fun ConnectionBannerStalePreview() {
    ConnectionBanner(
        brokerMode = "LIVE",
        serverWsState = WsState.CONNECTED,
        brokerConnected = true,
        wsFeedHealthy = false,
        pingMs = 38L
    )
}
