package com.nagabyte.scalpx.core

import org.junit.Assert.*
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun `rupeesTextToAlignedPaise converts decimal rupee strings and floors to 5-paise tick`() {
        // Exact ticks
        assertEquals(11005L, MoneyFormatter.rupeesTextToAlignedPaise("110.05"))
        assertEquals(10000L, MoneyFormatter.rupeesTextToAlignedPaise("100.00"))
        assertEquals(10000L, MoneyFormatter.rupeesTextToAlignedPaise("100"))
        assertEquals(5L, MoneyFormatter.rupeesTextToAlignedPaise("0.05"))

        // Unaligned inputs floored to 5-paise tick (110.07 -> 11005L)
        assertEquals(11005L, MoneyFormatter.rupeesTextToAlignedPaise("110.07"))
        assertEquals(11005L, MoneyFormatter.rupeesTextToAlignedPaise("110.09"))
        assertEquals(11000L, MoneyFormatter.rupeesTextToAlignedPaise("110.04"))
        assertEquals(123450L, MoneyFormatter.rupeesTextToAlignedPaise("1234.54"))
    }

    @Test
    fun `rupeesTextToAlignedPaise returns null on zero, negative, or invalid input`() {
        assertNull(MoneyFormatter.rupeesTextToAlignedPaise("0"))
        assertNull(MoneyFormatter.rupeesTextToAlignedPaise("0.00"))
        assertNull(MoneyFormatter.rupeesTextToAlignedPaise("-10.00"))
        assertNull(MoneyFormatter.rupeesTextToAlignedPaise("abc"))
        assertNull(MoneyFormatter.rupeesTextToAlignedPaise(""))
        assertNull(MoneyFormatter.rupeesTextToAlignedPaise("   "))
        assertNull(MoneyFormatter.rupeesTextToAlignedPaise("12.34.56"))
    }

    @Test
    fun `formatPaise formats paise with 2 decimals and optional rupee symbol`() {
        assertEquals("₹110.05", MoneyFormatter.formatPaise(11005L, includeSymbol = true))
        assertEquals("110.05", MoneyFormatter.formatPaise(11005L, includeSymbol = false))
        assertEquals("₹100.00", MoneyFormatter.formatPaise(10000L, includeSymbol = true))
        assertEquals("₹0.05", MoneyFormatter.formatPaise(5L, includeSymbol = true))
        assertEquals("₹0.00", MoneyFormatter.formatPaise(0L, includeSymbol = true))

        // Negative P&L formatting (-4500L -> "-₹45.00" / "-45.00")
        assertEquals("-₹45.00", MoneyFormatter.formatPaise(-4500L, includeSymbol = true))
        assertEquals("-45.00", MoneyFormatter.formatPaise(-4500L, includeSymbol = false))
        assertEquals("-₹0.05", MoneyFormatter.formatPaise(-5L, includeSymbol = true))
    }

    @Test
    fun `roundDownToTick and roundUpToTick properly adjust values`() {
        assertEquals(11000L, MoneyFormatter.roundDownToTick(11004L, 5L))
        assertEquals(11005L, MoneyFormatter.roundDownToTick(11005L, 5L))
        assertEquals(11005L, MoneyFormatter.roundDownToTick(11009L, 5L))

        assertEquals(11005L, MoneyFormatter.roundUpToTick(11001L, 5L))
        assertEquals(11005L, MoneyFormatter.roundUpToTick(11005L, 5L))
        assertEquals(11010L, MoneyFormatter.roundUpToTick(11006L, 5L))
    }

    @Test
    fun `isTickAligned correctly validates 5-paise multiples`() {
        assertTrue(MoneyFormatter.isTickAligned(11005L, 5L))
        assertTrue(MoneyFormatter.isTickAligned(11000L, 5L))
        assertFalse(MoneyFormatter.isTickAligned(11003L, 5L))
        assertFalse(MoneyFormatter.isTickAligned(11001L, 5L))
        assertFalse(MoneyFormatter.isTickAligned(0L, 5L))
        assertFalse(MoneyFormatter.isTickAligned(-5L, 5L))
    }

    @Test
    fun `toPaise converts string or throws on invalid`() {
        assertEquals(11005L, MoneyFormatter.toPaise("110.05"))
        try {
            MoneyFormatter.toPaise("invalid")
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }
}
