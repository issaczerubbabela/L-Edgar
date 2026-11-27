package com.issaczerubbabel.ledgar.capture.pair

import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** The Ledger's side of [LedgerMatcher]: the Transactions worth comparing and those a capture already resolved. */
data class LedgerCandidates(val transactions: List<ExpenseRecord>, val linkedIds: Set<Long>) {
    fun bestMatch(capture: CapturedTransaction, zone: ZoneId = ZoneId.systemDefault()): ExpenseRecord? =
        LedgerMatcher.bestMatch(capture, transactions, linkedIds, zone)

    companion object {
        val EMPTY = LedgerCandidates(emptyList(), emptySet())
    }
}

/** Reads what [LedgerMatcher] compares a capture with from Room, once or as it changes. */
class LedgerMatchFinder @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val captureDao: CaptureDao
) {

    /** The best match for [capture] right now, or null. */
    suspend fun find(capture: CapturedTransaction, zone: ZoneId = ZoneId.systemDefault()): ExpenseRecord? {
        val date = LedgerMatcher.dateOf(capture, zone)
        val transactions = expenseDao.getCaptureMatchCandidates(
            from = date.minusDays(LedgerMatcher.WINDOW_DAYS).toString(),
            to = date.plusDays(LedgerMatcher.WINDOW_DAYS).toString()
        )
        return LedgerMatcher.bestMatch(capture, transactions, captureDao.getLinkedExpenseIds().toSet(), zone)
    }

    /**
     * Every Transaction from [LedgerMatcher.LOOKBACK_DAYS] before [today] on, as it changes, so a
     * capture picks up its match the moment the user saves the Transaction it repeats.
     */
    fun observe(today: LocalDate = LocalDate.now()): Flow<LedgerCandidates> = combine(
        expenseDao.observeCaptureMatchCandidates(today.minusDays(LedgerMatcher.LOOKBACK_DAYS).toString()),
        captureDao.observeLinkedExpenseIds()
    ) { transactions, linked -> LedgerCandidates(transactions, linked.toSet()) }
}
