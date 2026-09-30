package com.issaczerubbabel.ledgar.capture.parse

/**
 * Google Pay / Paytm payment notifications. Google Pay's layout is taken from a description of its
 * notifications ("Paid ₹500 to X." then "₹500.00 debited from Bank Account (XXXX1234). UPI Ref:
 * ..."), not from a verified capture off a real phone, so its golden tests only prove the parser
 * matches that description. Paytm has no sample at all yet and is matched by the same patterns
 * on trust. [sender] is the posting app's label; the package-name mapping belongs to the
 * notification listener.
 */
class UpiAppParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean =
        SENDER_HINT.containsMatchIn(sender) && ParsingUtils.AMOUNT.containsMatchIn(text)

    override fun parse(text: String): ParsedTxn? {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        if (ParsingUtils.OTP.containsMatchIn(normalized)) return null
        val amount = ParsingUtils.amountOf(normalized) ?: return null

        val paid = PAID_TO.find(normalized)
        val received = RECEIVED_FROM.find(normalized)
        val direction = when {
            paid != null -> Direction.DEBIT
            received != null -> Direction.CREDIT
            else -> return null
        }

        return ParsedTxn(
            amount = amount,
            direction = direction,
            merchantRaw = (paid ?: received)?.groupValues?.get(1)?.trim(),
            accountHint = MASKED_ACCOUNT.find(normalized)?.groupValues?.get(1),
            refNumber = ParsingUtils.REF.find(normalized)?.groupValues?.get(1),
            channel = Channel.UPI
        )
    }

    companion object {
        private val SENDER_HINT = Regex("""google pay|gpay|paytm""", RegexOption.IGNORE_CASE)

        private const val AMOUNT_PART = """(?:₹|rs\.?|inr)\s*[\d,]+(?:\.\d{1,2})?"""

        /** The payee runs until the next sentence starts with an amount, or the text ends. */
        private const val PAYEE_END = """(?=\.?\s+(?:₹|rs\.?|inr)\s*\d|\.?$)"""

        private val PAID_TO = Regex(
            """\bpaid\s+$AMOUNT_PART\s+to\s+(.+?)$PAYEE_END""",
            RegexOption.IGNORE_CASE
        )
        private val RECEIVED_FROM = Regex(
            """\breceived\s+$AMOUNT_PART\s+from\s+(.+?)$PAYEE_END""",
            RegexOption.IGNORE_CASE
        )

        /** "Bank Account (XXXX1234)" */
        private val MASKED_ACCOUNT = Regex("""\(\s*[x*]+(\d{3,4})\s*\)""", RegexOption.IGNORE_CASE)
    }
}
