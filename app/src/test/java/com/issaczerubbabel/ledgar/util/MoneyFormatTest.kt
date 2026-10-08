package com.issaczerubbabel.ledgar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun formatsWholeRupeesWithTheRupeeSign() {
        assertEquals("₹0", formatRupees(0.0))
        assertEquals("₹940", formatRupees(940.0))
        assertEquals("₹24,380", formatRupees(24380.0))
    }

    @Test
    fun groupsInLakhsTheIndianWay() {
        assertEquals("₹1,20,000", formatRupees(120000.0))
        assertEquals("₹12,34,567", formatRupees(1234567.0))
    }

    @Test
    fun roundsToTheNearestRupeeInsteadOfShowingPaise() {
        assertEquals("₹1,483", formatRupees(1483.33))
        assertEquals("₹1,484", formatRupees(1483.5))
    }

    @Test
    fun putsTheMinusBeforeTheSign() {
        assertEquals("-₹940", formatRupees(-940.0))
        assertEquals("-₹1,20,000", formatRupees(-120000.0))
    }

    @Test
    fun exactFormatKeepsPaiseOnlyWhenThereAreSome() {
        assertEquals("₹450", formatRupeesExact(450.0))
        assertEquals("₹212.40", formatRupeesExact(212.4))
        assertEquals("₹1,20,000.05", formatRupeesExact(120000.05))
        assertEquals("₹0.50", formatRupeesExact(0.5))
        assertEquals("-₹940.25", formatRupeesExact(-940.25))
        assertEquals("₹0", formatRupeesExact(0.0))
    }

    @Test
    fun moneyAlwaysShowsPaiseWithIndianGrouping() {
        assertEquals("₹0.00", formatMoney(0.0))
        assertEquals("₹2,47,980.50", formatMoney(247980.5))
        assertEquals("₹1,23,45,678.00", formatMoney(12345678.0))
        assertEquals("₹999.99", formatMoney(999.99))
    }

    @Test
    fun moneyUsesATrueMinusAndAPlusOnlyWhenAsked() {
        assertEquals("−₹18,640.00", formatMoney(-18640.0))
        assertEquals("₹340.00", formatMoney(340.0))
        assertEquals("+₹340.00", formatMoney(340.0, signed = true))
        assertEquals("−₹340.00", formatMoney(-340.0, signed = true))
        assertEquals("₹0.00", formatMoney(0.0, signed = true))
    }

    @Test
    fun moneyRoundsToPaiseAndNeverShowsMinusZero() {
        assertEquals("₹0.30", formatMoney(0.1 + 0.2))
        assertEquals("₹0.00", formatMoney(-0.004))
        assertEquals("₹0.00", formatMoney(0.1 + 0.2 - 0.3))
        assertEquals("₹1,483.34", formatMoney(1483.336))
    }

    @Test
    fun aFieldShowsNoTrailingPointZero() {
        assertEquals("68000", amountToInput(68000.0))
        assertEquals("0", amountToInput(0.0))
        assertEquals("12.5", amountToInput(12.5))
    }

    @Test
    fun parsesWhatAPersonMightType() {
        assertEquals(68000.0, parseAmountInput("68000")!!, 0.0)
        assertEquals(68000.0, parseAmountInput("68,000")!!, 0.0)
        assertEquals(68000.5, parseAmountInput(" 68 000.5 ")!!, 0.0)
        assertEquals(0.5, parseAmountInput(".5")!!, 0.0)
    }

    @Test
    fun refusesAnythingThatIsNotAFiniteNumber() {
        assertNull(parseAmountInput(""))
        assertNull(parseAmountInput("abc"))
        assertNull(parseAmountInput("1.2.3"))
        assertNull(parseAmountInput("NaN"))
        assertNull(parseAmountInput("Infinity"))
    }
}
