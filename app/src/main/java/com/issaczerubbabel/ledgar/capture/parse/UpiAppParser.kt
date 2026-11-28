package com.issaczerubbabel.ledgar.capture.parse

/**
 * Google Pay / Paytm payment notifications, as the listener hands them over: the title, a
 * newline, then the text. Google Pay's two formats were read off a real phone:
 *
 * - money received: `NAME paid you ₹1.00` / `Tap to view.` (the amount is only in the title)
 * - Autopay: `Payment successful` / `Payment for Autopay of ₹2,000.00 to PAYEE was successful. Tap to view.`
 *
 * Google Pay posts nothing for an ordinary payment sent, so the bank's alert covers that, and it
 * never names the Account. Anything else it posts with an amount (offers, cashback, loans) is not a
 * payment and parses to null. Paytm has no sample yet and is matched by the same patterns on
 * trust. [sender] is the posting app's label; the package-name mapping belongs to the listener.
 */
class UpiAppParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean =
        SENDER_HINT.containsMatchIn(sender) && ParsingUtils.AMOUNT.containsMatchIn(text)

    override fun parse(text: String): ParsedTxn? {
        if (ParsingUtils.OTP.containsMatchIn(text)) return null
        // The received pattern is the title line alone, so it needs the newline that normalising removes.
        val received = PAID_YOU.find(text)
        val autopay = AUTOPAY.find(ParsingUtils.normalizeWhitespace(text))
        val (match, direction) = when {
            received != null -> received to Direction.CREDIT
            autopay != null -> autopay to Direction.DEBIT
            else -> return null
        }

        return ParsedTxn(
            amount = match.groups["amount"]!!.value.replace(",", "").toDoubleOrNull() ?: return null,
            direction = direction,
            merchantRaw = match.groups["party"]!!.value.trim(),
            accountHint = null,
            refNumber = ParsingUtils.REF.find(text)?.groupValues?.get(1),
            channel = Channel.UPI
        )
    }

    companion object {
        private val SENDER_HINT = Regex("""google pay|gpay|paytm""", RegexOption.IGNORE_CASE)

        private const val NAMED_AMOUNT = """(?:₹|rs\.?|inr)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)"""

        /** The title line on its own: "NAME paid you ₹1.00". */
        private val PAID_YOU = Regex(
            """^\s*(?<party>\S.*?)\s+paid\s+you\s+$NAMED_AMOUNT\s*$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )

        /** "Payment for Autopay of ₹2,000.00 to PAYEE was successful." */
        private val AUTOPAY = Regex(
            """\bpayment\s+for\s+autopay\s+of\s+$NAMED_AMOUNT\s+to\s+(?<party>.+?)\s+was\s+successful\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
