package com.issaczerubbabel.ledgar.capture.parse

/**
 * Recognises a Not-a-payment alert: one that shows an amount but reports no money moving. Each
 * phrase names one kind; keep them narrow, since a match drops the alert without a trace. A
 * refund or cashback actually credited, and a mandate's real debit ("UPI Mandate: Sent Rs.…"),
 * must never match.
 */
object NotAPayment {

    private fun phrase(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private const val AMOUNT = """(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?"""

    private val PHRASES = listOf(
        // Payment requests: "<NAME> has requested money from you", "requested Rs. 150.00 from you".
        phrase("""\bhas\s+requested\b"""),
        phrase("""\brequested\s+(?:money|$AMOUNT)"""),
        // Deductions and refunds still to come.
        phrase("""\bwill\s+be\s+(?:debited|deducted|refunded)\b"""),
        // Mandates set up, announced or ended; their real debits read "UPI Mandate: Sent Rs.…".
        phrase("""\bmandate\s+(?:set|alert)\b"""),
        phrase("""\bmandate\b.{0,100}?\b(?:created|revoked|cancelled|paused)\b"""),
        phrase("""\binstal{1,2}ment\s+due\b"""),
        // Payments that never went through.
        phrase("""\bdeclined\b"""),
        phrase("""\bhas\s+(?:failed|expired)\b"""),
        // Refunds only initiated: "Refund of Rs 150 has been initiated", "refund for your trxn … initiated".
        phrase("""\brefund\b.{0,80}?\binitiated\b"""),
        // Offers.
        phrase("""\bfree\s*\d+(?:\.\d+)?\s*gb\b"""),
        phrase("""\bwelcome\s+(?:bonus|rewards?)\b"""),
        phrase("""\bup\s+to\s+$AMOUNT\s+in\b"""),
        phrase("""\bemi\s+starts\s+at\b"""),
        phrase("""\bearn\b.{0,30}?\bcashback\b"""),
        phrase("""\bcashback\s+on\b.{0,40}?\brecharge\b""")
    )

    fun matches(text: String): Boolean {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        return PHRASES.any { it.containsMatchIn(normalized) }
    }
}
