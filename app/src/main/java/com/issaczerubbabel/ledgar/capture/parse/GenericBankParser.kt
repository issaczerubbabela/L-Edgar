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
        val direction = ParsingUtils.directionOf(normalized) ?: return null

        val merchant = if (direction == Direction.DEBIT) {
            CREDITED_COUNTERPARTY.find(normalized)?.groupValues?.get(1) ?: MERCHANT.find(normalized)?.groupValues?.get(1)
        } else {
            MERCHANT.find(normalized)?.groupValues?.get(1)
        }

        return ParsedTxn(
            amount = amount,
            direction = direction,
            merchantRaw = merchant?.takeUnless { ALL_DIGITS.matches(it) },
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

        /** "...for Rs 240.00 on 28-Sep-26; ZOMATO credited." — the payee named just before "credited". */
        private val CREDITED_COUNTERPARTY = Regex(
            """;\s*([A-Za-z][A-Za-z0-9 &.'\*\-]{1,40}?)\s+credited\b""",
            RegexOption.IGNORE_CASE
        )

        private val ALL_DIGITS = Regex("""\d+""")
    }
}
