package com.issaczerubbabel.ledgar.capture.parse

/**
 * Best-effort parser for Google Pay / Paytm payment notifications, based on commonly seen public
 * wording rather than a real sample of either app's actual text. Unlike [HdfcParser]/[CubParser],
 * this one has no golden test yet — treat its output as unverified until real (redacted)
 * notification text is available to check it against, and expect its patterns to need revising
 * then. [sender] is loosely matched against either the posting app's label or the notification
 * title, since the real package-name-based lookup belongs to the not-yet-built notification
 * listener.
 */
class UpiAppParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean =
        SENDER_HINT.containsMatchIn(sender) && ParsingUtils.AMOUNT.containsMatchIn(text)

    override fun parse(text: String): ParsedTxn? {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        if (ParsingUtils.OTP.containsMatchIn(normalized)) return null
        val amount = ParsingUtils.amountOf(normalized) ?: return null

        val direction = when {
            PAID_TO.containsMatchIn(normalized) -> Direction.DEBIT
            RECEIVED_FROM.containsMatchIn(normalized) -> Direction.CREDIT
            else -> return null
        }

        val merchantRaw = if (direction == Direction.DEBIT) {
            PAID_TO.find(normalized)?.groupValues?.get(1)
        } else {
            RECEIVED_FROM.find(normalized)?.groupValues?.get(1)
        }

        return ParsedTxn(
            amount = amount,
            direction = direction,
            merchantRaw = merchantRaw,
            accountHint = null,
            refNumber = null,
            channel = Channel.UPI
        )
    }

    companion object {
        private val SENDER_HINT = Regex("""google pay|gpay|paytm""", RegexOption.IGNORE_CASE)
        private val PAID_TO = Regex(
            """paid\s+(?:to\s+)?([A-Za-z0-9][A-Za-z0-9 &.'\-]{1,40}?)(?=\s+(?:using|via|from)\b|$)""",
            RegexOption.IGNORE_CASE
        )
        private val RECEIVED_FROM = Regex(
            """received\s+from\s+([A-Za-z0-9][A-Za-z0-9 &.'\-]{1,40}?)(?=\s+(?:using|via)\b|$)""",
            RegexOption.IGNORE_CASE
        )
    }
}
