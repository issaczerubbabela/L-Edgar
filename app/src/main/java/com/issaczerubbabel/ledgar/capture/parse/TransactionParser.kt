package com.issaczerubbabel.ledgar.capture.parse

/** One bank or UPI app's alert format. [canParse] should be cheap; [parse] does the real work. */
interface TransactionParser {
    fun canParse(sender: String, text: String): Boolean
    fun parse(text: String): ParsedTxn?
}
