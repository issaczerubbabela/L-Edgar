package com.issaczerubbabel.ledgar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.math.BigDecimal
import org.junit.Test

class AmountInputTest {

    private fun digit(current: String, value: Int) =
        applyKeypadAction(current, KeypadAction.Digit(value))

    private fun dot(current: String) = applyKeypadAction(current, KeypadAction.Dot)

    private fun backspace(current: String) = applyKeypadAction(current, KeypadAction.Backspace)

    private fun op(current: String, operator: AmountOperator) = applyKeypadAction(current, KeypadAction.Operator(operator))

    private fun type(keys: String): String = keys.fold("") { amount, key ->
        when (key) {
            '.' -> dot(amount)
            '+' -> op(amount, AmountOperator.PLUS)
            '-' -> op(amount, AmountOperator.MINUS)
            '*' -> op(amount, AmountOperator.TIMES)
            '/' -> op(amount, AmountOperator.DIVIDE)
            else -> digit(amount, key.digitToInt())
        }
    }

    private fun value(input: String, allowNegative: Boolean = false) =
        (evaluateAmount(input, allowNegative) as? AmountResult.Value)?.amount

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

    @Test
    fun anOperatorFollowsANumber() {
        assertEquals("1240-", type("1240-"))
        assertTrue(isAmountExpression("1240-"))
        assertFalse(isAmountExpression("1240"))
    }

    @Test
    fun aSecondOperatorReplacesTheFirst() {
        assertEquals("1240-", type("1240+-"))
    }

    @Test
    fun anOperatorOnAnEmptyAmountIsIgnored() {
        assertEquals("", type("+"))
        assertEquals("", type("*"))
    }

    @Test
    fun backspaceStepsBackThroughOperators() {
        assertEquals("1240", backspace("1240-"))
        assertEquals("1240-", backspace("1240-1"))
    }

    @Test
    fun digitAndDotRulesApplyToTheLastNumber() {
        assertEquals("5.25+0.", type("5.25+."))
        assertEquals("5.25+0.12", type("5.25+.129"))
        assertEquals("5+7", type("5+07"))
    }

    @Test
    fun anExpressionHoldsAtMostEightNumbers() {
        val eight = type("1+1+1+1+1+1+1+1")
        assertEquals(eight, op(eight, AmountOperator.PLUS))
    }

    @Test
    fun timesAndDivideBindTighterThanPlusAndMinus() {
        assertEquals(BigDecimal("14.00"), value(type("2+3*4")))
        assertEquals(BigDecimal("1090.00"), value(type("1240-150")))
        assertEquals(BigDecimal("105.00"), value(type("3*20+45")))
        assertEquals(BigDecimal("1190.00"), value(type("2380/2")))
    }

    @Test
    fun resultsRoundHalfUpToThePaisa() {
        assertEquals(BigDecimal("33.33"), value(type("100/3")))
        assertEquals(BigDecimal("0.30"), value(type("0.1+0.2")))
        assertEquals(BigDecimal("0.67"), value(type("2/3")))
    }

    @Test
    fun aTrailingOperatorIsLeftOutOfTheResult() {
        assertEquals(BigDecimal("1240.00"), value("1240-"))
    }

    @Test
    fun divisionByZeroCannotBeUsed() {
        assertEquals(AmountResult.Invalid, evaluateAmount(type("5/0")))
    }

    @Test
    fun aResultOfZeroOrBelowCannotBeUsedExceptForASignedAdjustment() {
        assertEquals(AmountResult.Invalid, evaluateAmount(type("100-200")))
        assertEquals(AmountResult.Invalid, evaluateAmount(type("100-100")))
        assertEquals(BigDecimal("-100.00"), value(type("100-200"), allowNegative = true))
        assertEquals(AmountResult.Invalid, evaluateAmount(type("100-100"), allowNegative = true))
    }

    @Test
    fun anEditedAdjustmentKeepsItsLeadingMinus() {
        assertFalse(isAmountExpression("-340"))
        assertEquals("-340+", op("-340", AmountOperator.PLUS))
        assertEquals(BigDecimal("-310.00"), value("-340+30", allowNegative = true))
        assertEquals("-", op("-", AmountOperator.PLUS))
    }

    @Test
    fun aResultOverTheLengthCapCannotBeUsed() {
        assertEquals(AmountResult.Invalid, evaluateAmount(type("999999999999*999999999999")))
    }

    @Test
    fun evaluateCollapsesTheExpressionToItsResult() {
        assertEquals("1090", applyKeypadAction(type("1240-150"), KeypadAction.Evaluate))
        assertEquals("33.33", applyKeypadAction(type("100/3"), KeypadAction.Evaluate))
        assertEquals("0.3", applyKeypadAction(type("0.1+0.2"), KeypadAction.Evaluate))
        assertEquals("5/0", applyKeypadAction("5/0", KeypadAction.Evaluate))
    }

    @Test
    fun aPlainNumberEvaluatesToItself() {
        assertEquals(BigDecimal("1240.00"), value("1240"))
        assertEquals(AmountResult.Empty, evaluateAmount(""))
    }

    @Test
    fun theExpressionIsShownGroupedWithTypographicOperators() {
        assertEquals("1,24,000 − 150", formatAmountExpression("124000-150"))
        assertEquals("3 × 20 + 4.5", formatAmountExpression("3*20+4.5"))
        assertEquals("1,240 ÷", formatAmountExpression("1240/"))
        assertEquals("-340 + 30", formatAmountExpression("-340+30"))
    }
}
