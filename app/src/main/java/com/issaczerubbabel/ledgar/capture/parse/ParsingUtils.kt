package com.issaczerubbabel.ledgar.capture.parse

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
