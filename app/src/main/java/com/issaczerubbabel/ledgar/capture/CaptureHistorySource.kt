package com.issaczerubbabel.ledgar.capture

import com.issaczerubbabel.ledgar.capture.categorize.PastTransaction
import com.issaczerubbabel.ledgar.data.local.dao.AccountAliasDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** What the on-phone guessers read from Room, so the ingestor can be tested without it. */
interface CaptureHistorySource {
    /** Every Expense, Income and Transfer (Transfers only feed the Suggested description), rebuilt from Room on each call: nothing is cached or stored. */
    suspend fun pastTransactions(): List<PastTransaction>

    /** The Accounts that aliases seen on [sender]'s alerts point to. */
    suspend fun accountsAliasedForSender(sender: String): Set<Long>
}

class RoomCaptureHistorySource @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val aliasDao: AccountAliasDao
) : CaptureHistorySource {

    override suspend fun pastTransactions(): List<PastTransaction> {
        val zone = ZoneId.systemDefault()
        return expenseDao.predictionHistory().mapNotNull { row ->
            val date = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: return@mapNotNull null
            PastTransaction(
                id = row.id,
                type = row.type,
                category = row.category,
                description = row.description,
                amount = row.amount,
                date = date,
                merchantNorm = row.merchantNorm,
                minuteOfDay = row.txnTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
                    ?.let { it.hour * 60 + it.minute }
            )
        }
    }

    override suspend fun accountsAliasedForSender(sender: String): Set<Long> =
        aliasDao.accountIdsAliasedForSender(sender).toSet()
}
