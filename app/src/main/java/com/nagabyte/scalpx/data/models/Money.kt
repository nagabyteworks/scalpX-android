package com.nagabyte.scalpx.data.models

import com.nagabyte.scalpx.core.MoneyFormatter

/**
 * Backward compatibility alias for com.nagabyte.scalpx.core.MoneyFormatter.
 */
object Money {
    const val TICK_PAISE_DEFAULT = MoneyFormatter.TICK_PAISE_DEFAULT

    fun toPaise(rupees: String): Long = MoneyFormatter.toPaise(rupees)

    fun fromPaise(paise: Long, includeSymbol: Boolean = false): String =
        MoneyFormatter.formatPaise(paise, includeSymbol)

    fun roundDownToTick(paise: Long, tickPaise: Long = TICK_PAISE_DEFAULT): Long =
        MoneyFormatter.roundDownToTick(paise, tickPaise)

    fun roundUpToTick(paise: Long, tickPaise: Long = TICK_PAISE_DEFAULT): Long =
        MoneyFormatter.roundUpToTick(paise, tickPaise)

    fun isTickAligned(paise: Long, tickPaise: Long = TICK_PAISE_DEFAULT): Boolean =
        MoneyFormatter.isTickAligned(paise, tickPaise)
}
