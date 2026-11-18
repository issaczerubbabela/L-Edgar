package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.bucket.RestoredBucket
import com.issaczerubbabel.ledgar.data.bucket.RestoredCycle
import com.issaczerubbabel.ledgar.data.bucket.StartCycleRequest
import com.issaczerubbabel.ledgar.data.bucket.StartCycleResult
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BucketAccountsTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repo: BucketBudgetRepositoryImpl
    private var rd = 0L
    private var sip = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BucketBudgetRepositoryImpl(db, db.bucketBudgetDao())
        rd = db.accountDao().insert(AccountRecord(groupName = "Savings", accountName = "Recurring Deposit", initialBalance = 0.0))
        sip = db.accountDao().insert(AccountRecord(groupName = "Investments", accountName = "HDFC Large Cap Fund", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `an Account moves between Buckets and follows into the next cycle`() = runBlocking {
        val cycle = repo.insertCycle(BudgetCycle(startDate = "2026-10-25", endDate = "2026-11-24", spendableAmount = 1.0))
        val savings = repo.insertBucket(BudgetBucket(cycleId = cycle, name = "Savings", colorIndex = 0, allocatedAmount = 1.0))
        val other = repo.insertBucket(BudgetBucket(cycleId = cycle, name = "Other", colorIndex = 0, allocatedAmount = 1.0))

        repo.setBucketAccounts(cycle, other, setOf(rd))
        repo.setBucketAccounts(cycle, savings, setOf(rd, sip))
        assertEquals(setOf(savings), repo.observeAccountRoutes(cycle).first().map { it.bucketId }.toSet())

        val next = (repo.startCycle(
            StartCycleRequest(LocalDate.parse("2026-11-25"), LocalDate.parse("2026-12-24"), 1.0, carryOverBuckets = true),
            LocalDate.parse("2026-11-25")
        ) as StartCycleResult.Started).cycleId
        assertEquals(setOf(rd, sip), repo.observeAccountRoutes(next).first().map { it.accountId }.toSet())

        db.accountDao().delete(db.accountDao().getAccountById(sip)!!)
        assertEquals("deleting an Account stops it being tracked", listOf(rd), repo.observeAccountRoutes(next).first().map { it.accountId })
    }

    @Test
    fun `a restore matches tracked Accounts by name and drops names with no Account`() = runBlocking {
        repo.replaceAllFromBackup(
            listOf(
                RestoredCycle(
                    startDate = "2026-10-25", endDate = "2026-11-24", spendableAmount = 1.0, closedAt = null,
                    buckets = listOf(
                        RestoredBucket(
                            name = "Savings", note = "", colorIndex = 0, emoji = "", allocatedAmount = 1.0, sortOrder = 0,
                            categories = emptyList(), accounts = listOf("recurring deposit", "Closed FD")
                        )
                    )
                )
            )
        )

        val snapshot = repo.getBackupSnapshot()
        assertEquals(listOf(rd), snapshot.accountRoutes.map { it.accountId })
        assertTrue(snapshot.accountNames.getValue(rd) == "Recurring Deposit")
    }
}
