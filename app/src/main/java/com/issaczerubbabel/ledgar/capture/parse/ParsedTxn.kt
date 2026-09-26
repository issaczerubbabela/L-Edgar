package com.issaczerubbabel.ledgar.capture.parse

enum class Direction { DEBIT, CREDIT }

enum class Channel { UPI, CARD, NEFT, IMPS, ATM, UNKNOWN }

/** What a [TransactionParser] extracts from one alert, before categorization runs. */
data class ParsedTxn(
    val amount: Double,
    val direction: Direction,
    val merchantRaw: String?,
    val accountHint: String?,
    val refNumber: String?,
    val channel: Channel,
    /** The other side of a two-account narration (CUB's "credited to a/c ..." style). */
    val counterpartyAccountHint: String? = null
)
