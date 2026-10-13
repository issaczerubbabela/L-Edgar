package com.issaczerubbabel.ledgar.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** The four keypad operators. The amount string stores [symbol]; the screen shows [display]. */
enum class AmountOperator(val symbol: Char, val display: String, val spoken: String) {
    PLUS('+', "+", "Plus"),
    MINUS('-', "−", "Minus"),
    TIMES('*', "×", "Times"),
    DIVIDE('/', "÷", "Divided by");

    companion object {
        fun of(symbol: Char): AmountOperator? = entries.firstOrNull { it.symbol == symbol }
    }
}

/** Actions the custom numeric keypad can emit against an amount input string. */
sealed class KeypadAction {
    data class Digit(val value: Int) : KeypadAction()
    object Dot : KeypadAction()
    object Backspace : KeypadAction()
    data class Operator(val operator: AmountOperator) : KeypadAction()

    /** Collapses an expression to its result, when it has one that can be used. */
    object Evaluate : KeypadAction()
}

/** What an amount string works out to. */
sealed interface AmountResult {
    data object Empty : AmountResult
    data class Value(val amount: BigDecimal) : AmountResult

    /** Division by zero, a result that can't be saved (zero, or below zero where that isn't allowed), or too long. */
    data object Invalid : AmountResult
}

private const val MAX_DECIMALS = 2
private const val MAX_LENGTH = 12
private const val MAX_NUMBERS = 8

/**
 * Applies a single keypad action to an amount-input string, keeping it always in a valid
 * intermediate state ("", "5", "5.", "5.2", "5.25", "1240-", "1240-150" — never "5..", "05",
 * "1240+-" or a number over-length).
 *
 * The string may hold an expression of up to [MAX_NUMBERS] numbers joined by `+ - * /`. Digit and
 * dot rules apply to the last number only. A leading `-` is a sign, not an operator: an edited
 * Balance adjustment starts from a negative amount.
 */
fun applyKeypadAction(current: String, action: KeypadAction): String = when (action) {
    is KeypadAction.Digit -> withLastNumber(current) { appendDigit(it, action.value) }
    KeypadAction.Dot -> withLastNumber(current) { appendDot(it) }
    KeypadAction.Backspace -> current.dropLast(1)
    is KeypadAction.Operator -> appendOperator(current, action.operator)
    KeypadAction.Evaluate -> when (val result = evaluateAmount(current, allowNegative = true)) {
        is AmountResult.Value -> result.amount.stripTrailingZeros().toPlainString()
        else -> current
    }
}

/** True when [input] holds an operator, so the keypad's commit key works it out before it saves. */
fun isAmountExpression(input: String): Boolean = input.drop(1).any { AmountOperator.of(it) != null }

/**
 * Works out [input]: `×` and `÷` before `+` and `−`, no brackets, rounded half-up to the paisa. A
 * trailing operator is left out. Only a Balance adjustment ([allowNegative]) may come out below zero;
 * nothing may come out as zero.
 */
fun evaluateAmount(input: String, allowNegative: Boolean = false): AmountResult {
    val tokens = tokens(input).let { if (it.lastOrNull() is Token.Op) it.dropLast(1) else it }
    if (tokens.isEmpty()) return AmountResult.Empty

    // Fold × and ÷ into their left-hand number first, then add up the terms.
    val terms = mutableListOf<BigDecimal>()
    var pending: AmountOperator? = null
    for (token in tokens) {
        when (token) {
            is Token.Op -> pending = token.operator
            is Token.Num -> {
                val number = token.text.toBigDecimalOrNull() ?: return AmountResult.Invalid
                when (pending) {
                    null, AmountOperator.PLUS -> terms += number
                    AmountOperator.MINUS -> terms += number.negate()
                    AmountOperator.TIMES -> terms[terms.lastIndex] = terms.last().multiply(number)
                    AmountOperator.DIVIDE -> {
                        if (number.signum() == 0) return AmountResult.Invalid
                        terms[terms.lastIndex] = terms.last().divide(number, MathContext.DECIMAL64)
                    }
                }
            }
        }
    }
    val result = terms.fold(BigDecimal.ZERO, BigDecimal::add).setScale(MAX_DECIMALS, RoundingMode.HALF_UP)
    return when {
        result.signum() == 0 -> AmountResult.Invalid
        result.signum() < 0 && !allowNegative -> AmountResult.Invalid
        result.stripTrailingZeros().toPlainString().length > MAX_LENGTH -> AmountResult.Invalid
        else -> AmountResult.Value(result)
    }
}

/** The amount [input] works out to, or null when it's empty or can't be used. */
fun amountValue(input: String, allowNegative: Boolean = false): Double? =
    (evaluateAmount(input, allowNegative) as? AmountResult.Value)?.amount?.toDouble()

/** [input] as the amount area shows an expression: "1,24,000 − 150", grouped the Indian way. */
fun formatAmountExpression(input: String): String = tokens(input).joinToString(" ") { token ->
    when (token) {
        is Token.Op -> token.operator.display
        is Token.Num -> groupTyped(token.text)
    }
}

private sealed interface Token {
    data class Num(val text: String) : Token
    data class Op(val operator: AmountOperator) : Token
}

/** Splits "1240-150" into numbers and operators; a leading "-" stays with the first number. */
private fun tokens(input: String): List<Token> {
    val tokens = mutableListOf<Token>()
    val number = StringBuilder()
    input.forEachIndexed { index, char ->
        val operator = AmountOperator.of(char)
        if (operator != null && index > 0) {
            if (number.isNotEmpty()) tokens += Token.Num(number.toString())
            number.clear()
            tokens += Token.Op(operator)
        } else {
            number.append(char)
        }
    }
    if (number.isNotEmpty()) tokens += Token.Num(number.toString())
    return tokens
}

/** Applies [change] to the number after the last operator, leaving the rest of the expression as it is. */
private fun withLastNumber(current: String, change: (String) -> String): String {
    val split = current.indexOfLast { AmountOperator.of(it) != null }.takeIf { it > 0 } ?: -1
    val prefix = current.substring(0, split + 1)
    return prefix + change(current.substring(split + 1))
}

private fun appendOperator(current: String, operator: AmountOperator): String {
    val last = current.lastOrNull() ?: return current
    if (current == "-") return current
    if (current.length > 1 && AmountOperator.of(last) != null) return current.dropLast(1) + operator.symbol
    if (tokens(current).count { it is Token.Num } >= MAX_NUMBERS) return current
    return current + operator.symbol
}

private fun appendDigit(current: String, digit: Int): String {
    require(digit in 0..9) { "digit must be 0-9, was $digit" }

    val dotIndex = current.indexOf('.')
    if (dotIndex != -1 && current.length - dotIndex - 1 >= MAX_DECIMALS) return current
    if (current.length >= MAX_LENGTH) return current

    return when {
        current.isEmpty() -> digit.toString()
        current == "0" -> if (digit == 0) current else digit.toString()
        else -> current + digit
    }
}

private fun appendDot(current: String): String {
    if (current.contains('.')) return current
    if (current.length >= MAX_LENGTH) return current
    return if (current.isEmpty()) "0." else "$current."
}

/** Groups the whole-rupee part of a number as typed ("124000.5" → "1,24,000.5"), keeping what follows as is. */
private fun groupTyped(number: String): String {
    val sign = if (number.startsWith("-")) "-" else ""
    val unsigned = number.removePrefix("-")
    val whole = unsigned.substringBefore('.')
    val rest = unsigned.substring(whole.length)
    if (whole.length <= 3) return sign + unsigned
    val lastThree = whole.takeLast(3)
    val grouped = whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "$sign$grouped,$lastThree$rest"
}
