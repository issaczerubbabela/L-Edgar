package com.issaczerubbabel.ledgar.capture.parse

/**
 * Recognises a Not-a-payment alert: one that shows an amount but reports no money moving. Each
 * phrase names one kind; keep them narrow, since a match drops the alert without a trace. An alert
 * that says money was credited ("has been credited", "credited to your A/c") is never one, so a
 * refund or cashback actually credited still parses, and so does a mandate's real debit
 * ("UPI Mandate: Sent Rs.…"), which no phrase names.
 */
object NotAPayment {

    private fun phrase(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private val AMOUNT = ParsingUtils.AMOUNT.pattern

    private val PHRASES = listOf(
        // Payment requests: "<NAME> has requested money from you", "requested Rs. 150.00 from you".
        phrase("""\bhas\s+requested\b"""),
        phrase("""\brequested\s+(?:money|$AMOUNT)"""),
        // An amount still to come: "INR 500.00 will be debited", "Rs.250.00 will be refunded".
        phrase("""$AMOUNT\s+will\s+be\s+(?:debited|deducted|refunded)\b"""),
        // Mandates set up, announced or ended.
        phrase("""\bmandate\s+(?:set|alert)\b"""),
        phrase("""\bmandate\b.{0,100}?\b(?:has\s+been|is|was)\s+(?:successfully\s+)?(?:created|revoked|cancelled|paused)\b"""),
        phrase("""\binstal{1,2}ment\s+due\b"""),
        // Payments that never went through.
        phrase("""\b(?:txn|transaction|payment)\s+(?:is\s+|was\s+|has\s+been\s+)?declined\b"""),
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

    /** Money that did arrive: "has been credited", "credited to HDFC Bank A/c", "credited back to". */
    private val CREDITED = phrase("""\b(?:has\s+been|is|was)\s+credited\b|\bcredited\s+(?:back\s+)?(?:to|in|into)\b""")

    fun matches(text: String): Boolean {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        return !CREDITED.containsMatchIn(normalized) && PHRASES.any { it.containsMatchIn(normalized) }
    }
}
