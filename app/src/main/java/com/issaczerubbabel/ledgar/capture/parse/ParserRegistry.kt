package com.issaczerubbabel.ledgar.capture.parse

/** Tries each parser in order and returns the first one that both claims and parses the text. */
class ParserRegistry(private val parsers: List<TransactionParser>) {

    fun parse(sender: String, text: String): ParsedTxn? {
        val parser = parsers.firstOrNull { it.canParse(sender, text) } ?: return null
        val parsed = parser.parse(text) ?: return null
        return if (parsed.txnDate != null) parsed else parsed.copy(txnDate = ParsingUtils.dateOf(text))
    }

    companion object {
        fun default(): ParserRegistry = ParserRegistry(
            listOf(CubParser(), HdfcParser(), AxisParser(), UpiAppParser(), GenericBankParser())
        )
    }
}
