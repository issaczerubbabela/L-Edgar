package com.issaczerubbabel.ledgar.capture

import com.issaczerubbabel.ledgar.capture.parse.Channel
import com.issaczerubbabel.ledgar.capture.parse.ParsedTxn
import com.issaczerubbabel.ledgar.capture.source.AlertSource
import com.issaczerubbabel.ledgar.capture.source.AlertSourceKind

/** One of a capture's Alerts, read again: [txnTime] is the moment it would file the capture under on its own. */
data class ReadAlert(
    val source: AlertSource,
    val sender: String,
    val parsed: ParsedTxn,
    val arrivedAt: Long,
    val txnTime: Long
)

/** What a capture says once its Alerts are put together. */
data class MergedFacts(
    /** The payment, with the best payee as its merchant and the bank's Account digits. */
    val parsed: ParsedTxn,
    /** The bank's own merchant text when another source named the payee differently, for a rule saved under it. */
    val bankMerchantRaw: String?,
    val txnTime: Long,
    /** The bank's sender, its own app's first, whose app may guess the Account; null with no bank Alert. */
    val bankSender: String?
)

/**
 * Puts one payment's Alerts together (ADR-0012), each fact from the source that states it best:
 * the payee from the UPI app, then the email, then the SMS or bank app; the Account digits only
 * from a bank's Alert; the date from the earliest Alert. Pure, so a merge, a split and a re-guess
 * always agree.
 */
object AlertMerge {

    fun facts(alerts: List<ReadAlert>): MergedFacts {
        require(alerts.isNotEmpty()) { "A capture has at least one Alert" }
        val byArrival = alerts.sortedBy { it.arrivedAt }
        val first = byArrival.first()
        val banks = byArrival.filter { it.source.isBank }
        val lead = banks.firstOrNull() ?: first

        val payee = byArrival.filter { !it.parsed.merchantRaw.isNullOrBlank() }.minByOrNull { payeeRank(it.source) }
        val bankMerchant = banks.firstNotNullOfOrNull { it.parsed.merchantRaw?.takeIf(String::isNotBlank) }
        val parsed = lead.parsed.copy(
            merchantRaw = payee?.parsed?.merchantRaw,
            accountHint = banks.firstNotNullOfOrNull { it.parsed.accountHint },
            counterpartyAccountHint = banks.firstNotNullOfOrNull { it.parsed.counterpartyAccountHint },
            refNumber = byArrival.firstNotNullOfOrNull { it.parsed.refNumber },
            channel = (banks + byArrival).map { it.parsed.channel }.firstOrNull { it != Channel.UNKNOWN } ?: Channel.UNKNOWN,
            txnDate = first.parsed.txnDate,
            kind = byArrival.firstNotNullOfOrNull { it.parsed.kind }
        )
        return MergedFacts(
            parsed = parsed,
            bankMerchantRaw = bankMerchant?.takeIf { it != parsed.merchantRaw },
            txnTime = first.txnTime,
            bankSender = (banks.firstOrNull { it.source.kind == AlertSourceKind.BANK_APP } ?: banks.firstOrNull())?.sender
        )
    }

    private fun payeeRank(source: AlertSource): Int = when (source.kind) {
        AlertSourceKind.UPI_APP -> 0
        AlertSourceKind.EMAIL -> 1
        AlertSourceKind.SMS, AlertSourceKind.BANK_APP -> 2
    }
}
