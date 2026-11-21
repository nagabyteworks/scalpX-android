package com.nagabyte.scalpx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.data.remote.dto.LadderPositionDto
import com.nagabyte.scalpx.ui.theme.TradingColors
import com.nagabyte.scalpx.ui.theme.TradingTypography
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayTradesBottomSheet(
    trades: ImmutableList<LadderPositionDto>,
    totalRealizedPnlPaise: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TradingColors.SurfaceCard,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Trades",
                    color = TradingColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = TradingColors.SurfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TradingColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Realized:",
                            color = TradingColors.TextSecondary,
                            fontSize = 11.sp
                        )
                        val pnlColor = if (totalRealizedPnlPaise >= 0) TradingColors.ProfitGreen else TradingColors.LossRed
                        Text(
                            text = MoneyFormatter.formatPaise(totalRealizedPnlPaise),
                            style = TradingTypography.TabularDigitsBold,
                            color = pnlColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (trades.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No closed trades today. Safe trading!",
                        color = TradingColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(trades) { trade ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = TradingColors.SurfaceElevated),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = trade.symbol,
                                        color = TradingColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${trade.side} ${trade.quantity} qty @ ${MoneyFormatter.formatPaise(trade.entryPricePaise)}",
                                        style = TradingTypography.TabularDigits,
                                        color = TradingColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Surface(
                                        color = if (trade.status == "EXITED" || trade.status == "CLOSED") TradingColors.ProfitGreen.copy(alpha = 0.2f) else TradingColors.WarningAmber.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = trade.status,
                                            color = if (trade.status == "EXITED" || trade.status == "CLOSED") TradingColors.ProfitGreen else TradingColors.WarningAmber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun TodayTradesBottomSheetPreview() {
    TodayTradesBottomSheet(
        trades = persistentListOf(
            LadderPositionDto(
                token = "26000",
                symbol = "NIFTY24DEC24500CE",
                side = "BUY",
                quantity = 50,
                entryPricePaise = 10000L,
                nextMilestonePaise = 11000L,
                stepSizePaise = 500L,
                breakoutBufferPaise = 100L,
                slippageBufferPaise = 150L,
                status = "EXITED"
            )
        ),
        totalRealizedPnlPaise = 50000L,
        onDismiss = {}
    )
}
