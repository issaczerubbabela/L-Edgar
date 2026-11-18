package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BucketAccount
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.remote.BucketImportDto
import com.issaczerubbabel.ledgar.data.remote.CycleImportDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** A Bucket that tracks Transfers into savings Accounts, such as an RD or a SIP. */
class BucketTransfersTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val cycle = BudgetCycle(id = 1, startDate = "2026-10-25", endDate = "2026-11-24", spendableAmount = 70000.0)
    private val savings = BudgetBucket(id = 1, cycleId = 1, name = "Savings", colorIndex = 0, allocatedAmount = 15000.0, sortOrder = 0)
    private val essentials = BudgetBucket(id = 2, cycleId = 1, name = "Essentials", colorIndex = 1, allocatedAmount = 20000.0, sortOrder = 1)

    private val bank = 10L
    private val rd = 11L
    private val sip = 12L
    private val emergency = 13L
    private val names = mapOf(bank to "City Union Bank", rd to "Recurring Deposit", sip to "HDFC Large Cap Fund", emergency to "Emergency Fund RD")
    private val routes = listOf(
        BucketAccount(cycleId = 1, bucketId = 1, accountId = rd),
        BucketAccount(cycleId = 1, bucketId = 1, accountId = sip)
    )

    private fun transfer(date: String, from: Long?, to: Long?, amount: Double) = ExpenseRecord(
        date = date, type = "Transfer", category = "Transfer", description = "", amount = amount, remarks = "",
        fromAccountId = from, toAccountId = to
    )

    private fun expense(date: String, category: String, amount: Double) =
        ExpenseRecord(date = date, type = "Expense", category = category, description = "", amount = amount, remarks = "")

    private fun build(records: List<ExpenseRecord>, closedAt: String? = null) = CycleSummaryBuilder.build(
        cycle.copy(closedAt = closedAt), listOf(savings, essentials), listOf(BucketCategory(cycleId = 1, bucketId = 2, category = "Rent")),
        records, today, routes, names
    )

    @Test
    fun `Transfers into tracked Accounts count towards their Bucket and the cycle`() {
        val summary = build(
            listOf(
                transfer("2026-11-01", bank, rd, 5000.0),
                transfer("2026-11-05", bank, sip, 3000.0),
                transfer("2026-11-06", rd, bank, 1000.0), // taken back out
                transfer("2026-11-07", bank, emergency, 2000.0), // not tracked
                expense("2026-11-02", "Rent", 14000.0)
            )
        )

        val bucket = summary.buckets.first { it.bucket.id == 1L }
        assertEquals(7000.0, bucket.spent, 0.0)
        assertEquals(
            listOf(AccountTransfers(sip, "HDFC Large Cap Fund", 3000.0), AccountTransfers(rd, "Recurring Deposit", 4000.0)).sortedByDescending { it.amount },
            bucket.accounts
        )
        assertEquals("Left to spend counts the savings Transfers as gone", 70000.0 - 14000.0 - 7000.0, summary.leftToSpend, 0.0)
        assertEquals(summary.buckets.sumOf { it.spent } + summary.unbucketedSpent, summary.totalSpent, 0.0)
    }

    @Test
    fun `moving money between a Bucket's own Accounts changes nothing`() {
        val summary = build(listOf(transfer("2026-11-03", rd, sip, 2500.0)))

        assertEquals(0.0, summary.buckets.first().spent, 0.0)
        assertEquals(0.0, summary.totalSpent, 0.0)
    }

    @Test
    fun `a closed cycle counts only Transfers up to its end date`() {
        val summary = build(
            listOf(transfer("2026-11-24", bank, rd, 5000.0), transfer("2026-11-25", bank, rd, 5000.0), transfer("2026-10-24", bank, rd, 5000.0)),
            closedAt = "2026-11-25"
        )

        assertEquals(5000.0, summary.buckets.first().spent, 0.0)
    }

    @Test
    fun `tracked Accounts are backed up by name and restored once each per cycle`() {
        val snapshot = BucketBudgetSnapshot(
            cycles = listOf(cycle),
            buckets = listOf(savings, essentials),
            assignments = emptyList(),
            accountRoutes = routes,
            accountNames = names
        )

        val dtos = BucketBackupMapper.toSyncDtos(snapshot)
        assertEquals(listOf("HDFC Large Cap Fund", "Recurring Deposit"), dtos.single().buckets[0].accounts)
        assertNull("a Bucket with no Accounts sends none, so the request is as before", dtos.single().buckets[1].accounts)

        val restored = BucketBackupMapper.fromImportDtos(
            listOf(
                CycleImportDto(
                    startDate = "2026-10-25", endDate = "2026-11-24", spendableAmount = 70000.0,
                    buckets = listOf(
                        BucketImportDto(name = "Savings", accounts = listOf("Recurring Deposit", " recurring deposit ")),
                        BucketImportDto(name = "Essentials", accounts = listOf("Recurring Deposit", "HDFC Large Cap Fund")),
                        BucketImportDto(name = "Old") // backed up by a script before version 3
                    )
                )
            )
        ).single()
        assertEquals(listOf("Recurring Deposit"), restored.buckets[0].accounts)
        assertEquals("an Account sits in one Bucket per cycle", listOf("HDFC Large Cap Fund"), restored.buckets[1].accounts)
        assertEquals(emptyList<String>(), restored.buckets[2].accounts)
    }
}
