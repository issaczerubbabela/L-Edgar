package com.issaczerubbabel.ledgar.capture.parse

/**
 * HDFC Bank SMS/notification alerts. Built and golden-tested against three real (redacted)
 * formats: a UPI P2P send, a UPI ATM withdrawal, and a deposit narration. A VPA-addressed debit
 * (e.g. "to VPA swiggy@icici") isn't covered by those samples yet, so [merchantRaw] can come back
 * null for that shape until a real sample is available to test against.
 */
class HdfcParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean =
        text.contains("hdfc", ignoreCase = true) && ParsingUtils.AMOUNT.containsMatchIn(text)

    override fun parse(text: String): ParsedTxn? {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        if (ParsingUtils.OTP.containsMatchIn(normalized)) return null

        val amount = ParsingUtils.amountOf(normalized) ?: return null
        val accountHint = ParsingUtils.ACCOUNT.find(normalized)?.groupValues?.get(1)
        val refNumber = ParsingUtils.REF.find(normalized)?.groupValues?.get(1)

        if (ATM_WITHDRAWAL.containsMatchIn(normalized)) {
            return ParsedTxn(
                amount = amount,
                direction = Direction.DEBIT,
                merchantRaw = "ATM Withdrawal",
                accountHint = accountHint,
                refNumber = refNumber,
                channel = Channel.ATM
            )
        }

        val direction = when {
            ParsingUtils.CREDIT_WORDS.containsMatchIn(normalized) -> Direction.CREDIT
            ParsingUtils.DEBIT_WORDS.containsMatchIn(normalized) -> Direction.DEBIT
            else -> return null
        }

        val merchantRaw = if (direction == Direction.CREDIT) {
            CREDIT_NARRATION.find(normalized)?.groupValues?.get(1)
        } else {
            P2P_RECIPIENT.find(normalized)?.groupValues?.get(1)
        }

        return ParsedTxn(
            amount = amount,
            direction = direction,
            merchantRaw = merchantRaw,
            accountHint = accountHint,
            refNumber = refNumber,
            channel = ParsingUtils.channelOf(normalized)
        )
    }

    companion object {
        private val ATM_WITHDRAWAL = Regex("""atm\s+withdrawal""", RegexOption.IGNORE_CASE)

        /** "Sent Rs.70.00 From HDFC Bank A/C *1234 To Mrs Jane Doe On 26/09/26 ..." */
        private val P2P_RECIPIENT = Regex(
            """\bto\s+([A-Za-z][A-Za-z .'\-]{2,40}?)(?=\s+on\b|$)""",
            RegexOption.IGNORE_CASE
        )

        /** "...deposited in HDFC Bank A/c XX1234 on 25-SEP-26 for WFISPL CREDIT.Avl bal ..." */
        private val CREDIT_NARRATION = Regex(
            """\bfor\s+([A-Za-z][A-Za-z0-9 &.'\-]{1,40}?)\s+credit\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
