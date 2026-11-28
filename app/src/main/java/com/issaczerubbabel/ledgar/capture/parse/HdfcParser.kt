package com.issaczerubbabel.ledgar.capture.parse

/**
 * HDFC Bank SMS/notification alerts. Golden-tested against real (redacted) formats: a UPI P2P
 * send, a UPI ATM withdrawal, a deposit narration, a NEFT "Amt Deducted!", a NetBanking "Payment
 * Successful!", and an RD booked or paid through mobile, whose RD digits are the counterparty. A
 * VPA-addressed debit (e.g. "to VPA swiggy@icici") takes the handle as [merchantRaw] via
 * [ParsingUtils.vpaOf]; that shape is only covered by a synthetic sample so far.
 */
class HdfcParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean =
        text.contains("hdfc", ignoreCase = true) && ParsingUtils.AMOUNT.containsMatchIn(text)

    override fun parse(text: String): ParsedTxn? {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        if (ParsingUtils.OTP.containsMatchIn(normalized)) return null

        val amount = ParsingUtils.amountOf(normalized) ?: return null
        val accountHint = (ParsingUtils.ACCOUNT.find(normalized) ?: BANK_ACCOUNT.find(normalized))?.groupValues?.get(1)
        val refNumber = ParsingUtils.REF.find(normalized)?.groupValues?.get(1)
        val rdHint = (RD_BOOKED.find(normalized) ?: RD_THROUGH_MOBILE.find(normalized))?.groupValues?.get(1)

        if (rdHint != null) {
            return ParsedTxn(
                amount = amount,
                direction = Direction.DEBIT,
                merchantRaw = null,
                accountHint = accountHint,
                refNumber = refNumber,
                channel = Channel.UNKNOWN,
                counterpartyAccountHint = rdHint
            )
        }

        NETBANKING_PAYMENT.find(normalized)?.let { payment ->
            return ParsedTxn(
                amount = amount,
                direction = Direction.DEBIT,
                merchantRaw = payment.groupValues[1],
                accountHint = accountHint,
                refNumber = refNumber,
                channel = Channel.UNKNOWN
            )
        }

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

        val direction = ParsingUtils.directionOf(normalized) ?: return null

        val merchantRaw = if (direction == Direction.CREDIT) {
            CREDIT_NARRATION.find(normalized)?.groupValues?.get(1)
        } else {
            ParsingUtils.vpaOf(normalized) ?: P2P_RECIPIENT.find(normalized)?.groupValues?.get(1)
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
        /** "debited from HDFC Bank XX1234 on ...": the account named with no "A/c" before it. */
        private val BANK_ACCOUNT = Regex("""\bhdfc\s+bank\s+[x*]+(\d{3,4})\b""", RegexOption.IGNORE_CASE)

        /** "RD Booked! Your HDFC Bank RD XX5678 is booked on 12OCT-26 for INR 2000" */
        private val RD_BOOKED = Regex("""\brd\s+booked\b.*?\brd\s+[x*]*(\d{3,4})\b""", RegexOption.IGNORE_CASE)

        /** "UPDATE: INR 2,000.00 debited from HDFC Bank XX1234 ... Info: RD through MOBILE-XXXXXXXXXX5678 :<NAME>." */
        private val RD_THROUGH_MOBILE = Regex("""\brd\s+through\s+mobile-[x*]*(\d{3,4})\b""", RegexOption.IGNORE_CASE)

        /** "Payment Successful! Rs. 2500.00 from A/c **********1234 to PAYUAMAZON via HDFC Bank NetBanking." */
        private val NETBANKING_PAYMENT = Regex(
            """\bpayment\s+successful\b.*?\bto\s+(.+?)\s+via\s+hdfc\s+bank\s+netbanking\b""",
            RegexOption.IGNORE_CASE
        )

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
