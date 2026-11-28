package com.issaczerubbabel.ledgar.capture.parse

import com.issaczerubbabel.ledgar.capture.AccountHint
import com.issaczerubbabel.ledgar.capture.CaptureKinds
import com.issaczerubbabel.ledgar.capture.source.BankEmails

/**
 * Amazon Pay's emails (#181), as Gmail's notification gives them: the subject, a newline, then the
 * preview. Money spent from Amazon Pay Balance reaches no bank, so these are its only record. Each
 * format is golden-tested against a real email: a balance payment (a debit, the order id as its
 * reference), a gift card added to the balance and a cashback (both credits into it). The balance
 * has no digits, so its Account alias is by source ([AccountHint.AMAZON_PAY_BALANCE]).
 *
 * It claims every email from [BankEmails.AMAZON_PAY] and reads only these formats, so anything else
 * lands under Alerts we couldn't read rather than being guessed at by the generic parser.
 */
class AmazonPayEmailParser : TransactionParser {

    override fun canParse(sender: String, text: String): Boolean = sender == BankEmails.AMAZON_PAY.displayName

    override fun parse(text: String): ParsedTxn? {
        val normalized = ParsingUtils.normalizeWhitespace(text)
        if (ParsingUtils.OTP.containsMatchIn(normalized)) return null
        val subject = ParsingUtils.normalizeWhitespace(text.trim().lineSequence().first())

        // Paid some other way (a card, UPI), the bank's own alert reports it, so only the balance is read.
        PAID.matchEntire(subject)?.takeIf { BALANCE.containsMatchIn(normalized) }?.let { m ->
            return txn(m, Direction.DEBIT, m.groups["merchant"]!!.value)?.copy(
                refNumber = ORDER_ID.find(normalized)?.groupValues?.get(1),
                accountHint = AccountHint.AMAZON_PAY_BALANCE
            )
        }
        if (GIFT_CARD.containsMatchIn(normalized)) {
            val m = RECEIVED.find(normalized) ?: return null
            return txn(m, Direction.CREDIT, m.groups["merchant"]!!.value)?.copy(accountHint = AccountHint.AMAZON_PAY_BALANCE)
        }
        CASHBACK.firstNotNullOfOrNull { it.find(normalized) }?.let { m ->
            return txn(m, Direction.CREDIT, "Amazon Pay Cashback")
                ?.copy(accountHint = AccountHint.AMAZON_PAY_BALANCE, kind = CaptureKinds.CASHBACK)
        }
        return null
    }

    private fun txn(match: MatchResult, direction: Direction, merchant: String): ParsedTxn? {
        val amount = match.groups["amount"]!!.value.replace(",", "").toDoubleOrNull() ?: return null
        return ParsedTxn(
            amount = amount,
            direction = direction,
            merchantRaw = merchant.trim().ifEmpty { null },
            accountHint = null,
            refNumber = null,
            channel = Channel.UNKNOWN
        )
    }

    companion object {
        private val OPTIONS = setOf(RegexOption.IGNORE_CASE)

        private const val AMOUNT = """(?:rs\.?|inr|₹)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)"""

        /** The whole subject "Rs 903.00 was paid on Amazon.in". */
        private val PAID = Regex("""$AMOUNT\s+was\s+paid\s+on\s+(?<merchant>.+?)""", OPTIONS)

        /** "Thank you for using Amazon Pay Balance": the money left the balance, not a card or bank. */
        private val BALANCE = Regex("""\busing\s+amazon\s+pay\s+balance\b""", OPTIONS)

        /** "Order ID 000-0000000-0000000". */
        private val ORDER_ID = Regex("""\border\s+id[:\s]*(\d{3}-\d{7}-\d{7})\b""", OPTIONS)

        /** The subject "Amazon Pay Gift Card added to your Amazon Pay balance", or the text's "has been added to". */
        private val GIFT_CARD = Regex("""\bgift\s+card\s+(?:has\s+been\s+)?added\s+to\s+your\s+amazon\s+pay\s+balance\b""", OPTIONS)

        /** "Received Amount Amazon Pay eGift Card ₹833.00". */
        private val RECEIVED = Regex("""\breceived\s+amount\s+(?<merchant>.{1,60}?)\s+$AMOUNT""", OPTIONS)

        /** The subject "Your cashback of ₹25.00 is here!", or the text's "Here's ₹25.00 cashback!". */
        private val CASHBACK = listOf(
            Regex("""\byour\s+cashback\s+of\s+$AMOUNT\s+is\s+here\b""", OPTIONS),
            Regex("""\bhere.s\s+$AMOUNT\s+cashback\b""", OPTIONS)
        )
    }
}
