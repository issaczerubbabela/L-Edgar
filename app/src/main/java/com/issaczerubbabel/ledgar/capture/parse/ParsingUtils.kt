package com.issaczerubbabel.ledgar.capture.parse

import java.time.DateTimeException
import java.time.LocalDate

/** Regex building blocks shared by every [TransactionParser]. */
internal object ParsingUtils {

    val AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    val DEBIT_WORDS = Regex(
        """\b(debited|spent|paid|sent|withdrawn|withdrawal|purchase)\b""",
        RegexOption.IGNORE_CASE
    )

    val CREDIT_WORDS = Regex(
        """\b(credited|received|deposited|refund(?:ed)?)\b""",
        RegexOption.IGNORE_CASE
    )

    val OTP = Regex("""\b(otp|one time password)\b""", RegexOption.IGNORE_CASE)

    /** Matches "A/C *1234", "A/c 1234", "a/c no. XXXXXXXX1234", "Card XX9876", etc. */
    val ACCOUNT = Regex(
        """(?:a\/?c(?:\s*no\.?)?|acct|card)\s*(?:no\.?)?\s*[x*]*(\d{3,4})\b""",
        RegexOption.IGNORE_CASE
    )

    /** Matches "Ref 123...", "UPI RRN:123...", "(UPI Ref no 123...)". */
    val REF = Regex(
        """(?:ref(?:erence)?|rrn)\s*(?:no|id)?[.:\s]*(\d{9,14})""",
        RegexOption.IGNORE_CASE
    )

    private val UPI_HINT = Regex("upi|vpa", RegexOption.IGNORE_CASE)
    private val ATM_HINT = Regex("""\batm\b""", RegexOption.IGNORE_CASE)
    private val CARD_HINT = Regex("card", RegexOption.IGNORE_CASE)
    private val NEFT_HINT = Regex("neft", RegexOption.IGNORE_CASE)
    private val IMPS_HINT = Regex("imps", RegexOption.IGNORE_CASE)

    /** "on 26/09/26", "on 25-SEP-26", "on 14-09-2026": the day comes first, as in Indian bank alerts. */
    private val DATE = Regex(
        """\bon\s+(\d{1,2})[-/ ]([A-Za-z]{3}|\d{1,2})[-/ ](\d{4}|\d{2})\b""",
        RegexOption.IGNORE_CASE
    )

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** The date an alert says the transaction happened, or null if it has none or it is not a real date. */
    fun dateOf(text: String): LocalDate? {
        val match = DATE.find(text) ?: return null
        val (day, monthText, yearText) = match.destructured
        val month = monthText.toIntOrNull() ?: (MONTHS.indexOf(monthText.lowercase()) + 1).takeIf { it > 0 } ?: return null
        val year = yearText.toInt().let { if (yearText.length == 2) 2000 + it else it }
        return try {
            LocalDate.of(year, month, day.toInt())
        } catch (e: DateTimeException) {
            null
        }
    }

    fun normalizeWhitespace(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    fun amountOf(text: String): Double? =
        AMOUNT.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

    fun channelOf(text: String): Channel = when {
        UPI_HINT.containsMatchIn(text) -> Channel.UPI
        ATM_HINT.containsMatchIn(text) -> Channel.ATM
        CARD_HINT.containsMatchIn(text) -> Channel.CARD
        NEFT_HINT.containsMatchIn(text) -> Channel.NEFT
        IMPS_HINT.containsMatchIn(text) -> Channel.IMPS
        else -> Channel.UNKNOWN
    }
}
