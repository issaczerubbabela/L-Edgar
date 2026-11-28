package com.issaczerubbabel.ledgar.capture.parse

/** What one Alert turned out to be. */
sealed interface AlertReading {
    data class Payment(val txn: ParsedTxn) : AlertReading

    /** A Not-a-payment alert: dropped, never a capture and never an Unparsed alert. */
    data object NotAPayment : AlertReading

    /** No parser could read it; it may still be kept as an Unparsed alert. */
    data object Unreadable : AlertReading
}

/**
 * Drops Not-a-payment alerts, then tries each parser in order and returns the first one that both
 * claims and parses the text.
 */
class ParserRegistry(private val parsers: List<TransactionParser>) {

    fun read(sender: String, text: String): AlertReading {
        if (NotAPayment.matches(text)) return AlertReading.NotAPayment
        val parser = parsers.firstOrNull { it.canParse(sender, text) } ?: return AlertReading.Unreadable
        val parsed = parser.parse(text) ?: return AlertReading.Unreadable
        return AlertReading.Payment(if (parsed.txnDate != null) parsed else parsed.copy(txnDate = ParsingUtils.dateOf(text)))
    }

    /** The payment [text] reports, or null for a Not-a-payment or unreadable alert. */
    fun parse(sender: String, text: String): ParsedTxn? = (read(sender, text) as? AlertReading.Payment)?.txn

    companion object {
        fun default(): ParserRegistry = ParserRegistry(
            listOf(CubParser(), HdfcParser(), AxisParser(), UpiAppParser(), GenericBankParser())
        )
    }
}
