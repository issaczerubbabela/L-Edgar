package com.issaczerubbabel.ledgar.util

/** Actions the custom numeric keypad can emit against an amount input string. */
sealed class KeypadAction {
    data class Digit(val value: Int) : KeypadAction()
    object Dot : KeypadAction()
    object Backspace : KeypadAction()
}

private const val MAX_DECIMALS = 2
private const val MAX_LENGTH = 12

/**
 * Applies a single keypad action to an amount-input string, keeping it always in a valid
 * intermediate state ("", "5", "5.", "5.2", "5.25" — never "5..", "05", or over-length).
 */
fun applyKeypadAction(current: String, action: KeypadAction): String = when (action) {
    is KeypadAction.Digit -> appendDigit(current, action.value)
    KeypadAction.Dot -> appendDot(current)
    KeypadAction.Backspace -> current.dropLast(1)
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
