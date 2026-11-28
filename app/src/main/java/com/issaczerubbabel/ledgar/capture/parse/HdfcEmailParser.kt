package com.issaczerubbabel.ledgar.capture.parse

import com.issaczerubbabel.ledgar.capture.source.BankEmails
import java.time.LocalDate

/**
 * HDFC's alert emails (#180), as Gmail's notification gives them: the subject, a newline, then the
 * preview. Each format is golden-tested against a real email: a UPI debit (payee in brackets, else
 * the VPA), a debit card payment (merchant after "at", the card's digits as the Account), an IMPS
 * or NEFT transfer between accounts, two UPI credits (the payer after the VPA, or as "Sender:") and a
 * deposit alert. Gmail's text for a threaded email starts without "Dear Customer", which no pattern needs.
 *
 * It claims every email from [BankEmails.HDFC] and reads only these formats, so an unknown one lands
 * under Alerts we couldn't read rather than being guessed at by the SMS parser.
 */
class HdfcEmailParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean = sender == BankEmails.HDFC.displayName

    override fun parse(text: String): ParsedTxn? {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        if (ParsingUtils.OTP.containsMatchIn(normalized)) return null
        val ref = (REF.find(normalized) ?: ParsingUtils.REF.find(normalized))?.groupValues?.get(1)

        fun txn(match: MatchResult, direction: Direction, merchant: String?, channel: Channel): ParsedTxn? {
            val amount = match.groups["amount"]!!.value.replace(",", "").toDoubleOrNull() ?: return null
            return ParsedTxn(
                amount = amount,
                direction = direction,
                merchantRaw = merchant?.trim()?.takeIf { it.isNotEmpty() },
                accountHint = match.groups["account"]?.value,
                refNumber = ref,
                channel = channel
            )
        }

        UPI_DEBIT.find(normalized)?.let { m ->
            return txn(m, Direction.DEBIT, m.groups["payee"]?.value ?: m.groups["vpa"]?.value, Channel.UPI)
        }
        CARD_DEBIT.find(normalized)?.let { m ->
            return txn(m, Direction.DEBIT, m.groups["merchant"]?.value, Channel.CARD)?.copy(txnDate = dateOf(m))
        }
        TRANSFER_DEBIT.find(normalized)?.let { m ->
            val channel = ParsingUtils.channelOf(m.groups["via"]?.value.orEmpty())
            return txn(m, Direction.DEBIT, null, channel)?.copy(counterpartyAccountHint = m.groups["other"]?.value)
        }
        UPI_CREDIT.find(normalized)?.let { m ->
            return txn(m, Direction.CREDIT, m.groups["payer"]?.value ?: m.groups["vpa"]?.value, Channel.UPI)
        }
        CREDIT_DETAILS.find(normalized)?.let { m ->
            return txn(m, Direction.CREDIT, m.groups["payer"]?.value ?: m.groups["vpa"]?.value, ParsingUtils.channelOf(normalized))
                ?.copy(txnDate = dateOf(m))
        }
        DEPOSIT.find(normalized)?.let { m ->
            return txn(m, Direction.CREDIT, m.groups["payer"]?.value, Channel.UNKNOWN)?.copy(txnDate = dateOf(m))
        }
        return null
    }

    private fun dateOf(match: MatchResult): LocalDate? {
        val day = match.groups["day"]?.value ?: return null
        return ParsingUtils.dateOf(day, match.groups["month"]!!.value, match.groups["year"]!!.value)
    }

    companion object {
        private val OPTIONS = setOf(RegexOption.IGNORE_CASE)

        private const val AMOUNT = """(?:rs\.?|inr|₹)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)"""

        /** "ending 1234", "ending xxxxxxxxxx1234", "**1234", "XX1234": the last digits. */
        private const val DIGITS = """[x*]*(?<account>\d{3,4})\b"""

        /** "UPI transaction reference no.: 1111", "IMPS Reference No: 1111", "reference number is 1111". */
        private val REF = Regex("""\breference\s+(?:no\.?|number)\s*(?:is\s+)?[.:\s]*(\d{9,14})""", OPTIONS)

        /** "Rs.2000.00 is debited from your account ending 1234 towards VPA x@validyes (PAYEE) on 28-11-26." */
        private val UPI_DEBIT = Regex(
            """$AMOUNT\s+is\s+debited\s+from\s+your\s+account\s+ending\s+$DIGITS\s+towards\s+vpa\s+(?<vpa>\S+?)""" +
                """(?:\s+\((?<payee>.+?)\))?\s+on\s+\d""",
            OPTIONS
        )

        /** "Rs.2399.00 is debited from your HDFC Bank Debit Card ending 5678 at MERCHANT on 18 Nov, 2026 at 15:47:45." */
        private val CARD_DEBIT = Regex(
            """$AMOUNT\s+is\s+debited\s+from\s+your\s+hdfc\s+bank\s+debit\s+card\s+ending\s+$DIGITS\s+at\s+(?<merchant>.+?)""" +
                """\s+on\s+(?<day>\d{1,2})\s+(?<month>[A-Za-z]{3,9}),?\s+(?<year>\d{4})\b""",
            OPTIONS
        )

        /** "INR 47,000.00 has been debited from your account ending xx1234 on 25-11-26 and credited to the account ending xx5678 via IMPS." */
        private val TRANSFER_DEBIT = Regex(
            """$AMOUNT\s+has\s+been\s+debited\s+from\s+your\s+account\s+ending\s+$DIGITS\s+on\s+\S+\s+and\s+credited\s+to\s+""" +
                """the\s+account\s+ending\s+[x*]*(?<other>\d{3,4})\b(?:\s+via\s+(?<via>[A-Za-z]+))?""",
            OPTIONS
        )

        /** "Rs. 1.00 is successfully credited to your account **1234 by VPA x@hdfcbank PAYER on 06-03-26." */
        private val UPI_CREDIT = Regex(
            """$AMOUNT\s+is\s+(?:successfully\s+)?credited\s+to\s+your\s+account\s+(?:ending\s+)?$DIGITS\s+by\s+vpa\s+""" +
                """(?<vpa>\S+?)(?:\s+(?<payer>.{1,60}?))?\s+on\s+\d""",
            OPTIONS
        )

        /**
         * "Rs.5.00 has been successfully credited to your HDFC Bank account ending in 1234. Transaction Details:
         * a. Date: 27-11-26 b. Sender: NAME(VPA: x@okaxis) c. UPI Reference No.: 1111".
         */
        private val CREDIT_DETAILS = Regex(
            """$AMOUNT\s+has\s+been\s+(?:successfully\s+)?credited\s+to\s+your\s+hdfc\s+bank\s+account\s+ending\s+(?:in\s+)?$DIGITS""" +
                """(?:.*?\bdate:\s*(?<day>\d{1,2})-(?<month>\d{1,2}|[A-Za-z]{3})-(?<year>\d{4}|\d{2})\b)?""" +
                """(?:.*?\bsender:\s*(?<payer>[^(]+?)\s*\(\s*vpa:\s*(?<vpa>[^)\s]+)\s*\))?""",
            OPTIONS
        )

        /**
         * "You have received a credit in your HDFC Bank account. ... Amount received: INR 1,23,456.00 Account: XX1234
         * Date: 26-NOV-2026 Reference Details: PAYER CREDIT". The preview can end before any of the last three.
         */
        private val DEPOSIT = Regex(
            """\breceived\s+a\s+credit\s+in\s+your\s+hdfc\s+bank\s+account\b.*?\bamount\s+received:\s*$AMOUNT""" +
                """(?:\s+account:\s*$DIGITS)?""" +
                """(?:\s+date:\s*(?<day>\d{1,2})-(?<month>[A-Za-z]{3}|\d{1,2})-(?<year>\d{4}|\d{2})\b)?""" +
                """(?:\s+reference\s+details:\s*(?<payer>.+?)\s+credit\b)?""",
            OPTIONS
        )
    }
}
