package com.nagabyte.scalpx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.ui.scalper.BookTab
import com.nagabyte.scalpx.ui.theme.ScalperTheme
import com.nagabyte.scalpx.ui.theme.TradingTypography

/**
 * Zone 5: Book Header (Positions / Order Book / Trade Book tabs + Table/Cards toggle)
 * + Net Qty & MTM P&L Summary Bar.
 */
@Composable
fun Zone5BookHeader(
    activeTab: BookTab,
    isTableView: Boolean,
    positionsCount: Int,
    ordersCount: Int,
    tradesCount: Int,
    netQty: Int,
    totalPnlPaise: Long,
    onSelectTab: (BookTab) -> Unit,
    onToggleTableView: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
    ) {
        // Tab Row + Table/Card View Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BookTabItem(
                    title = "Positions",
                    count = positionsCount,
                    isSelected = activeTab == BookTab.POSITIONS,
                    onClick = { onSelectTab(BookTab.POSITIONS) }
                )

                BookTabItem(
                    title = "Order book",
                    count = ordersCount,
                    isSelected = activeTab == BookTab.ORDER_BOOK,
                    onClick = { onSelectTab(BookTab.ORDER_BOOK) }
                )

                BookTabItem(
                    title = "Trade book",
                    count = tradesCount,
                    isSelected = activeTab == BookTab.TRADE_BOOK,
                    onClick = { onSelectTab(BookTab.TRADE_BOOK) }
                )
            }

            // Cards vs Table view toggle (Only active for Positions tab)
            if (activeTab == BookTab.POSITIONS) {
                IconButton(
                    onClick = { onToggleTableView(!isTableView) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isTableView) Icons.Default.ViewAgenda else Icons.Default.TableChart,
                        contentDescription = if (isTableView) "Switch to Cards View" else "Switch to Table View",
                        tint = if (isTableView) colors.sellRed else colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = colors.border, thickness = 1.dp)

        // Summary Bar: Net Qty (Left) | MTM P&L (Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Net Qty
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Net Qty:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )
                Text(
                    text = "$netQty",
                    style = TradingTypography.TabularDigitsBold,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
            }

            // MTM P&L
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MTM:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )

                val isPositive = totalPnlPaise >= 0
                val pnlColor = if (isPositive) colors.profitGreenText else colors.lossRedText
                val sign = if (isPositive) "+" else ""

                Text(
                    text = "₹$sign${MoneyFormatter.formatPaise(totalPnlPaise)}",
                    style = TradingTypography.TabularDigitsBold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = pnlColor
                )
            }
        }

        HorizontalDivider(color = colors.border, thickness = 1.dp)
    }
}

@Composable
private fun BookTabItem(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = ScalperTheme.colors

    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val label = if (count > 0) "$title ($count)" else title
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.sellRed else colors.textSecondary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Red active underline indicator (exact 1Cliq styling)
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(if (isSelected) colors.sellRed else Color.Transparent)
        )
    }
}
