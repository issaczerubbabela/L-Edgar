package com.issaczerubbabel.ledgar.account

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.parseFlexibleDate

/**
 * What AccountMath and FundMath need of [this] record. It's dated by its date, or by its remote
 * timestamp when the date can't be read; null when neither can, and then nothing counts it.
 */
fun ExpenseRecord.toTransactionSnapshot(): TransactionSnapshot? {
    val date = parseFlexibleDate(date) ?: remoteTimestamp?.let(::parseFlexibleDate) ?: return null
    return TransactionSnapshot(
        id = id,
        type = type,
        date = date,
        amount = amount,
        accountId = accountId,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId
    )
}
