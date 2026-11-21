package com.nagabyte.scalpx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.ui.scalper.WsState
import com.nagabyte.scalpx.ui.theme.ScalperTheme
import com.nagabyte.scalpx.ui.theme.TradingTypography

/**
 * Zone 1: Pinned Top Header Bar (~44dp height)
 * Left: "Broker: Flattrade (<USER_ID>)" + Mode Badge (PAPER/LIVE)
 * Right: WS ping dot + Latency + Daily OTP button (if disconnected) + Theme toggle + Settings
 */
@Composable
fun Zone1BrokerHeader(
    brokerMode: String,
    serverWsState: WsState,
    brokerConnected: Boolean,
    wsFeedHealthy: Boolean,
    pingMs: Long,
    userId: String,
    isDarkTheme: Boolean,
    onConnectOtp: () -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Broker Name + User ID + Mode Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                val userLabel = if (userId.isNotBlank()) " ($userId)" else ""
                Text(
                    text = "Broker: Flattrade$userLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val isLive = brokerMode.equals("LIVE", ignoreCase = true)
                val badgeBg = if (isLive) colors.liveMode.copy(alpha = 0.15f) else colors.paperMode.copy(alpha = 0.15f)
                val badgeTextColor = if (isLive) colors.liveMode else colors.paperMode
                val badgeBorderColor = if (isLive) colors.liveMode else colors.paperMode

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .border(1.dp, badgeBorderColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isLive) "LIVE" else "PAPER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = badgeTextColor
                    )
                }
            }

            // Right: Connection Dot & Ping + OTP Connect + Theme + Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // WS Dot + Ping
                val dotColor = when {
                    serverWsState == WsState.CONNECTED && wsFeedHealthy -> colors.buyGreen
                    serverWsState == WsState.CONNECTING -> colors.paperMode
                    else -> colors.sellRed
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = "${pingMs}ms",
                        style = TradingTypography.TabularDigits,
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }

                // If broker not connected, show prominent OTP login chip
                if (!brokerConnected) {
                    Button(
                        onClick = onConnectOtp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.liveMode,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Connect OTP",
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "OTP Login",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Theme Toggle Icon
                IconButton(
                    onClick = { onToggleTheme(!isDarkTheme) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Settings Icon
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
