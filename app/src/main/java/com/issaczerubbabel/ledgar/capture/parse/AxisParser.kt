package com.issaczerubbabel.ledgar.capture.parse

/**
 * Axis Bank alerts. Golden-tested against two real formats: a UPI debit whose narration carries
 * "UPI/P2M/<ref>/<merchant>", and an IMPS credit. The UPI form never says "Axis", so it is
 * recognised by its narration instead of the bank name.
 */
class AxisParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean =
        ParsingUtils.AMOUNT.containsMatchIn(text) &&
            (text.contains("axis", ignoreCase = true) || UPI_NARRATION.containsMatchIn(text))

    override fun parse(text: String): ParsedTxn? {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        if (ParsingUtils.OTP.containsMatchIn(normalized)) return null

        val amount = ParsingUtils.amountOf(normalized) ?: return null
        val direction = when {
            ParsingUtils.CREDIT_WORDS.containsMatchIn(normalized) -> Direction.CREDIT
            ParsingUtils.DEBIT_WORDS.containsMatchIn(normalized) -> Direction.DEBIT
            else -> return null
        }

        val narration = UPI_NARRATION.find(normalized)
        return ParsedTxn(
            amount = amount,
            direction = direction,
            merchantRaw = narration?.groupValues?.get(2)?.trim(),
            accountHint = ParsingUtils.ACCOUNT.find(normalized)?.groupValues?.get(1),
            refNumber = narration?.groupValues?.get(1)
                ?: ParsingUtils.REF.find(normalized)?.groupValues?.get(1),
            channel = if (narration != null) Channel.UPI else ParsingUtils.channelOf(normalized)
        )
    }

    companion object {
        /** "...via UPI/P2M/435476373861/Shri Ujagar Fuels. Avail. Bal: INR 5,420.10." */
        private val UPI_NARRATION = Regex(
            """\bUPI/[A-Za-z0-9]+/(\d{9,14})/(.+?)(?=\.\s*Avail|\.\s*$|$)""",
            RegexOption.IGNORE_CASE
        )
    }
}
