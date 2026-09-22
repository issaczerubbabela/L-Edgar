package com.issaczerubbabel.ledgar.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    private fun digit(current: String, value: Int) =
        applyKeypadAction(current, KeypadAction.Digit(value))

    private fun dot(current: String) = applyKeypadAction(current, KeypadAction.Dot)

    private fun backspace(current: String) = applyKeypadAction(current, KeypadAction.Backspace)

    @Test
    fun firstDigitFromEmptyStartsTheAmount() {
        assertEquals("5", digit("", 5))
    }

    @Test
    fun digitsAppendInSequence() {
        assertEquals("245", digit(digit(digit("", 2), 4), 5))
    }

    @Test
    fun leadingZeroIsReplacedByNextNonZeroDigit() {
        assertEquals("5", digit("0", 5))
    }

    @Test
    fun secondZeroAfterLeadingZeroIsIgnored() {
        assertEquals("0", digit("0", 0))
    }

    @Test
    fun dotOnEmptyStartsWithZero() {
        assertEquals("0.", dot(""))
    }

    @Test
    fun dotAfterDigitsIsAppended() {
        assertEquals("5.", dot("5"))
    }

    @Test
    fun secondDotIsIgnored() {
        assertEquals("5.2", dot(digit(dot("5"), 2)))
    }

    @Test
    fun decimalDigitsAreCappedAtTwo() {
        val afterTwoDecimals = digit(digit(dot("5"), 2), 5) // "5.25"
        assertEquals("5.25", afterTwoDecimals)
        assertEquals("5.25", digit(afterTwoDecimals, 9))
    }

    @Test
    fun backspaceRemovesExactlyOneCharacter() {
        assertEquals("5.2", backspace("5.25"))
        assertEquals("5.", backspace("5.2"))
        assertEquals("5", backspace("5."))
        assertEquals("", backspace("5"))
    }

    @Test
    fun backspaceOnEmptyStaysEmpty() {
        assertEquals("", backspace(""))
    }

    @Test
    fun lengthIsCappedRegardlessOfDigitsOrDots() {
        var amount = ""
        repeat(30) { amount = digit(amount, 9) }
        assertEquals(12, amount.length)
        // further digits and dots are no-ops once capped
        assertEquals(amount, digit(amount, 3))
        assertEquals(amount, dot(amount))
    }
}
