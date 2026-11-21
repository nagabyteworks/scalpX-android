package com.nagabyte.scalpx.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TradingColors {
    val Background = Color(0xFF0D1117)
    val SurfaceCard = Color(0xFF161B22)
    val SurfaceElevated = Color(0xFF21262D)
    val Border = Color(0xFF30363D)

    val ProfitGreen = Color(0xFF00C853)
    val BuyCe = Color(0xFF00C853)
    val LossRed = Color(0xFFFF3B30)
    val BuyPe = Color(0xFFFF3B30)
    val PanicRed = Color(0xFFD50000)

    val WarningAmber = Color(0xFFFFB300)
    val PaperMode = Color(0xFFFFB300)
    val LiveMode = Color(0xFFFF1744)

    val ExchangeSlBg = Color(0xFF0D47A1)
    val ExchangeSlBorder = Color(0xFF1976D2)
    val ExchangeSlText = Color(0xFFE3F2FD)

    val LocalTriggerBg = Color(0xFF261C02)
    val LocalTriggerBorder = Color(0xFFFFB300)
    val LocalTriggerText = Color(0xFFFFE082)

    val TextPrimary = Color(0xFFF0F6FC)
    val TextSecondary = Color(0xFF8B949E)
    val TextMuted = Color(0xFF6E7681)
}

object TradingTypography {
    val TabularDigits = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum"
    )

    val TabularDigitsBold = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    )

    val PriceDisplay = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        fontFeatureSettings = "tnum"
    )

    val PnlLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        fontFeatureSettings = "tnum"
    )
}
