package com.nagabyte.scalpx.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 1Cliq-style high-contrast theme color system supporting both light and dark modes.
 */
data class ScalperThemeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val buyGreen: Color,
    val sellRed: Color,
    val profitGreenText: Color,
    val lossRedText: Color,
    val paperMode: Color,
    val liveMode: Color,
    val exchangeSlBg: Color,
    val exchangeSlBorder: Color,
    val exchangeSlText: Color,
    val localTriggerBg: Color,
    val localTriggerBorder: Color,
    val localTriggerText: Color
)

val LightScalperColors = ScalperThemeColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F8FA),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEEF1F5),
    border = Color(0xFFE0E3E7),
    textPrimary = Color(0xFF1F2937),
    textSecondary = Color(0xFF6B7280),
    textMuted = Color(0xFF9CA3AF),
    buyGreen = Color(0xFF2E9E47),
    sellRed = Color(0xFFF44336),
    profitGreenText = Color(0xFF1E8E3E),
    lossRedText = Color(0xFFD93025),
    paperMode = Color(0xFFF59E0B),
    liveMode = Color(0xFFDC2626),
    exchangeSlBg = Color(0xFFE3F2FD),
    exchangeSlBorder = Color(0xFF1976D2),
    exchangeSlText = Color(0xFF0D47A1),
    localTriggerBg = Color(0xFFFFF8E1),
    localTriggerBorder = Color(0xFFFFB300),
    localTriggerText = Color(0xFFB78103)
)

val DarkScalperColors = ScalperThemeColors(
    isDark = true,
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceCard = Color(0xFF161B22),
    surfaceElevated = Color(0xFF21262D),
    border = Color(0xFF30363D),
    textPrimary = Color(0xFFF0F6FC),
    textSecondary = Color(0xFF8B949E),
    textMuted = Color(0xFF6E7681),
    buyGreen = Color(0xFF2E9E47),
    sellRed = Color(0xFFF44336),
    profitGreenText = Color(0xFF00C853),
    lossRedText = Color(0xFFFF3B30),
    paperMode = Color(0xFFFFB300),
    liveMode = Color(0xFFFF1744),
    exchangeSlBg = Color(0xFF0D47A1),
    exchangeSlBorder = Color(0xFF1976D2),
    exchangeSlText = Color(0xFFE3F2FD),
    localTriggerBg = Color(0xFF261C02),
    localTriggerBorder = Color(0xFFFFB300),
    localTriggerText = Color(0xFFFFE082)
)

fun getScalperColors(isDark: Boolean): ScalperThemeColors = if (isDark) DarkScalperColors else LightScalperColors

val LocalScalperTheme = staticCompositionLocalOf { LightScalperColors }

object ScalperTheme {
    val colors: ScalperThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalScalperTheme.current
}

/**
 * Retained for backward compatibility across existing widgets.
 */
object TradingColors {
    val Background = Color(0xFF0D1117)
    val SurfaceCard = Color(0xFF161B22)
    val SurfaceElevated = Color(0xFF21262D)
    val Border = Color(0xFF30363D)

    val ProfitGreen = Color(0xFF00C853)
    val BuyCe = Color(0xFF2E9E47)
    val LossRed = Color(0xFFFF3B30)
    val BuyPe = Color(0xFFF44336)
    val PanicRed = Color(0xFFD50000)

    val WarningAmber = Color(0xFFFFB300)
    val PaperMode = Color(0xFFF59E0B)
    val LiveMode = Color(0xFFDC2626)

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
