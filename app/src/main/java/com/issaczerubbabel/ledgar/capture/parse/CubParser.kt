package com.issaczerubbabel.ledgar.capture.parse

/**
 * City Union Bank's two-account narration: "Your a/c no. XXXXXXXX1234 is credited/debited for
 * Rs.X on <date> and debited from/credited to a/c no. XXXXXXXXNNNN (UPI Ref no NNN)". Direction
 * is always about "Your a/c" (the primary account this alert is about); the other account is only
 * a counterparty hint, not necessarily one of the user's own accounts.
 */
class CubParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        return text.contains("-cub", ignoreCase = true) || PATTERN.containsMatchIn(normalized)
    }

    override fun parse(text: String): ParsedTxn? {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        if (ParsingUtils.OTP.containsMatchIn(normalized)) return null

        val match = PATTERN.find(normalized) ?: return null
        val groups = match.groupValues
        val accountHint = groups[1]
        val verb = groups[2]
        val amount = groups[3].replace(",", "").toDoubleOrNull() ?: return null
        val counterpartyAccountHint = groups[6]

        val direction = if (verb.equals("credited", ignoreCase = true)) Direction.CREDIT else Direction.DEBIT
        val refNumber = ParsingUtils.REF.find(normalized)?.groupValues?.get(1)

        return ParsedTxn(
            amount = amount,
            direction = direction,
            merchantRaw = null,
            accountHint = accountHint,
            counterpartyAccountHint = counterpartyAccountHint,
            refNumber = refNumber,
            channel = Channel.UPI
        )
    }

    companion object {
        private val PATTERN = Regex(
            """your a\/?c\s*no\.?\s*[x*]*(\d{3,4})\s+is\s+(credited|debited)\s+for\s+rs\.?\s*""" +
                """([\d,]+(?:\.\d{1,2})?)\s+on\s+([\d\-]+)\s+and\s+(credited to|debited from)\s+""" +
                """a\/?c\s*no\.?\s*[x*]*(\d{3,4})""",
            RegexOption.IGNORE_CASE
        )
    }
}
