package com.issaczerubbabel.ledgar.account

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The one way an [ExpenseRecord] becomes what AccountMath and FundMath read. */
class TransactionSnapshotOfTest {

    private fun record(date: String, remoteTimestamp: String? = null) = ExpenseRecord(
        id = 5, date = date, type = "Transfer", category = "Transfer", description = "", amount = 250.0,
        accountId = 1, fromAccountId = 2, toAccountId = 3, remarks = "", remoteTimestamp = remoteTimestamp
    )

    @Test
    fun aReadableDateIsUsedAndEveryFieldIsCopied() {
        val snapshot = record("2026-10-05", remoteTimestamp = "2026-11-01T08:00:00Z").toTransactionSnapshot()

        assertEquals(TransactionSnapshot(5, "Transfer", LocalDate.of(2026, 10, 5), 250.0, 1, 2, 3), snapshot)
    }

    @Test
    fun anUnreadableDateFallsBackToTheRemoteTimestamp() {
        assertEquals(LocalDate.of(2026, 11, 1), record("", remoteTimestamp = "2026-11-01T08:00:00Z").toTransactionSnapshot()!!.date)
        assertNull(record("not a date").toTransactionSnapshot())
    }

    @Test
    fun theAccountBookUsesTheSameFallback() {
        val book = AccountBook.of(
            listOf(AccountRecord(id = 1, groupName = "Accounts", accountName = "HDFC", initialBalance = 0.0, initialBalanceDate = "2026-01-01", displayOrder = 0)),
            emptyList(),
            listOf(record("", remoteTimestamp = "2026-11-01T08:00:00Z"), record("not a date"))
        )

        assertEquals(listOf(LocalDate.of(2026, 11, 1)), book.transactions.map { it.date })
    }
}
