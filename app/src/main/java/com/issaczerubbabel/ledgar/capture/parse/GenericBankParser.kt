package com.issaczerubbabel.ledgar.capture.parse

/** Fallback for any bank alert not matched by a bank-specific parser. */
class GenericBankParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean =
        ParsingUtils.AMOUNT.containsMatchIn(text) &&
            (ParsingUtils.DEBIT_WORDS.containsMatchIn(text) || ParsingUtils.CREDIT_WORDS.containsMatchIn(text))

    override fun parse(text: String): ParsedTxn? {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        if (ParsingUtils.OTP.containsMatchIn(normalized)) return null

        val amount = ParsingUtils.amountOf(normalized) ?: return null
        val direction = when {
            ParsingUtils.CREDIT_WORDS.containsMatchIn(normalized) -> Direction.CREDIT
            ParsingUtils.DEBIT_WORDS.containsMatchIn(normalized) -> Direction.DEBIT
            else -> return null
        }

        return ParsedTxn(
            amount = amount,
            direction = direction,
            merchantRaw = MERCHANT.find(normalized)?.groupValues?.get(1),
            accountHint = ParsingUtils.ACCOUNT.find(normalized)?.groupValues?.get(1),
            refNumber = ParsingUtils.REF.find(normalized)?.groupValues?.get(1),
            channel = ParsingUtils.channelOf(normalized)
        )
    }

    companion object {
        private val MERCHANT = Regex(
            """\b(?:at|to|towards)\s+([A-Za-z0-9][A-Za-z0-9 &.'\*\-]{2,40}?)(?=\s+on\b|\s+via\b|[.,]|$)""",
            RegexOption.IGNORE_CASE
        )
    }
}
