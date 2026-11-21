package com.nagabyte.scalpx.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Financial Math & Precision Primitives for Android.
 * Invariant: Never use Float or Double for internal price or P&L arithmetic.
 * All prices are Long integer paise (11005L = ₹110.05).
 * NSE Tick size is 5 paise (0.05).
 */
object MoneyFormatter {
    const val TICK_PAISE_DEFAULT = 5L

    /**
     * Formats Long paise to rupee string with 2 decimals.
     * e.g., 11005L -> "₹110.05" (or "110.05" without symbol).
     * e.g., -4500L -> "-₹45.00" (or "-45.00" without symbol).
     */
    fun formatPaise(paise: Long, includeSymbol: Boolean = true): String {
        val absPaise = kotlin.math.abs(paise)
        val whole = absPaise / 100L
        val frac = absPaise % 100L
        val sign = if (paise < 0) "-" else ""
        val prefix = if (includeSymbol) "₹" else ""
        return String.format(Locale.US, "%s%s%d.%02d", sign, prefix, whole, frac)
    }

    /**
     * Converts Long paise to decimal rupee string without currency symbol.
     * e.g., 11005L -> "110.05".
     */
    fun fromPaise(paise: Long): String = formatPaise(paise, includeSymbol = false)

    /**
     * Parses a decimal rupee input string safely via BigDecimal, converts to Long integer paise,
     * floors to NSE tick (default 5 paise), and returns null if invalid, non-numeric, or <= 0.
     * e.g., "110.05" -> 11005L
     * e.g., "110.07" -> 11005L (floored to 5 paise tick)
     * e.g., "0.00", "-10", "abc" -> null
     */
    fun rupeesTextToAlignedPaise(input: String, tickPaise: Long = TICK_PAISE_DEFAULT): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val bd = BigDecimal(trimmed)
            if (bd <= BigDecimal.ZERO) return null
            val paise = bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
            if (paise <= 0L) null else roundDownToTick(paise, tickPaise)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts a rupee string to exact paise, throwing IllegalArgumentException if invalid or <= 0.
     */
    fun toPaise(rupees: String): Long {
        val aligned = rupeesTextToAlignedPaise(rupees, 1L)
        requireNotNull(aligned) { "Invalid rupee string: $rupees" }
        return aligned
    }

    fun roundDownToTick(paise: Long, tickPaise: Long = TICK_PAISE_DEFAULT): Long {
        require(paise > 0) { "Paise must be > 0" }
        require(tickPaise > 0) { "Tick paise must be > 0" }
        return (paise / tickPaise) * tickPaise
    }

    fun roundUpToTick(paise: Long, tickPaise: Long = TICK_PAISE_DEFAULT): Long {
        require(paise > 0) { "Paise must be > 0" }
        require(tickPaise > 0) { "Tick paise must be > 0" }
        return ((paise + tickPaise - 1) / tickPaise) * tickPaise
    }

    fun isTickAligned(paise: Long, tickPaise: Long = TICK_PAISE_DEFAULT): Boolean {
        if (paise <= 0 || tickPaise <= 0) return false
        return paise % tickPaise == 0L
    }
}
