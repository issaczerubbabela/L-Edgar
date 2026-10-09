package com.issaczerubbabel.ledgar.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Whole rupees with Indian digit grouping (the last three digits, then pairs), e.g. ₹24,380,
 * ₹1,20,000 or -₹940. Grouped by hand rather than through NumberFormat, whose en-IN data differs
 * between the JVM and Android and would make the output depend on where it runs.
 */
fun formatRupees(amount: Double): String {
    val rounded = amount.roundToLong()
    val digits = groupIndian(abs(rounded).toString())
    return if (rounded < 0) "-₹$digits" else "₹$digits"
}

/**
 * Like [formatRupees], but keeps the paise when there are any (₹212.40), so an amount someone is
 * about to confirm is never shown rounded. Whole amounts stay as ₹450.
 */
fun formatRupeesExact(amount: Double): String {
    val paise = abs(amount * 100).roundToLong()
    val whole = paise / 100
    val fraction = paise % 100
    val sign = if (amount < 0 && paise != 0L) "-" else ""
    val base = "₹" + groupIndian(whole.toString())
    return if (fraction == 0L) sign + base else sign + base + "." + fraction.toString().padStart(2, '0')
}

/**
 * An exact amount for statements and totals: always two decimals (₹2,47,980.50), a true minus sign
 * (−₹18,640.00) and, with [signed], a + on positive amounts (+₹340.00). Rounds to paise first, so a
 * sum that is zero to the paisa is ₹0.00, never −₹0.00.
 */
fun formatMoney(amount: Double, signed: Boolean = false): String {
    val paise = (amount * 100).roundToLong()
    val digits = groupIndian((abs(paise) / 100).toString())
    val fraction = (abs(paise) % 100).toString().padStart(2, '0')
    val sign = when {
        paise < 0 -> "−"
        signed && paise > 0 -> "+"
        else -> ""
    }
    return "$sign₹$digits.$fraction"
}

private fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val lastThree = digits.takeLast(3)
    val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "$rest,$lastThree"
}

/** The number as it should appear in an editable amount field: no symbol, no trailing ".0". */
fun amountToInput(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.roundToLong().toString() else amount.toString()

/** Reads an amount typed by the user, tolerating grouping commas and spaces. Null if not a number. */
fun parseAmountInput(input: String): Double? =
    input.replace(",", "").replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }
