package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** [RecurringRepositoryImpl.materializeDue] against a real (in-memory) Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class RecurringRepositoryImplTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repository: RecurringRepositoryImpl
    private var accountId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecurringRepositoryImpl(db, db.recurringRuleDao(), db.expenseDao(), db.accountDao())
        accountId = db.accountDao().insert(AccountRecord(groupName = "Cash", accountName = "Wallet", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    private fun monthlyRule(nextDate: String, autoAdd: Boolean = true, isPaused: Boolean = false) = RecurringRule(
        type = "Expense",
        category = "Rent",
        description = "Rent",
        amount = 15000.0,
        accountId = accountId,
        remarks = "",
        frequency = "MONTHLY",
        interval = 1,
        anchorDay = 1,
        startDate = "2026-06-01",
        nextDate = nextDate,
        autoAdd = autoAdd,
        isPaused = isPaused,
        createdAt = "2026-06-01"
    )

    @Test
    fun `catching up after three missed months creates exactly three Transactions`() = runBlocking {
        val ruleId = repository.createRule(monthlyRule(nextDate = "2026-07-01"))

        val created = repository.materializeDue(today = LocalDate.of(2026, 9, 1))

        assertEquals(3, created)
        val dates = db.expenseDao().getAllRecordsSnapshot().map { it.date }.sorted()
        assertEquals(listOf("2026-07-01", "2026-08-01", "2026-09-01"), dates)
        assertEquals("2026-10-01", repository.getById(ruleId)!!.nextDate)
        assertTrue(db.expenseDao().getAllRecordsSnapshot().all { it.recurringRuleId == ruleId })
    }

    @Test
    fun `running materializeDue twice never creates the same occurrence again`() = runBlocking {
        repository.createRule(monthlyRule(nextDate = "2026-07-01"))

        val today = LocalDate.of(2026, 9, 1)
        val first = repository.materializeDue(today)
        val second = repository.materializeDue(today)

        assertEquals(3, first)
        assertEquals(0, second)
        assertEquals(3, db.expenseDao().getAllRecordsSnapshot().size)
    }

    @Test
    fun `a rule that is not yet due creates nothing`() = runBlocking {
        repository.createRule(monthlyRule(nextDate = "2026-10-01"))

        val created = repository.materializeDue(today = LocalDate.of(2026, 9, 1))

        assertEquals(0, created)
        assertTrue(db.expenseDao().getAllRecordsSnapshot().isEmpty())
    }

    @Test
    fun `a paused rule is skipped`() = runBlocking {
        repository.createRule(monthlyRule(nextDate = "2026-07-01", isPaused = true))

        assertEquals(0, repository.materializeDue(today = LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `a rule whose account was deleted is skipped rather than creating a broken Transaction`() = runBlocking {
        val ruleId = repository.createRule(monthlyRule(nextDate = "2026-07-01"))
        db.accountDao().delete(db.accountDao().getAccountById(accountId)!!)
        assertNull("the rule's account is nulled, not the rule itself", repository.getById(ruleId)!!.accountId)

        val created = repository.materializeDue(today = LocalDate.of(2026, 9, 1))

        assertEquals(0, created)
        assertEquals("2026-07-01", repository.getById(ruleId)!!.nextDate)
    }

    @Test
    fun `deleting a rule leaves the Transactions it already created alone`() = runBlocking {
        val ruleId = repository.createRule(monthlyRule(nextDate = "2026-07-01"))
        repository.materializeDue(today = LocalDate.of(2026, 7, 1))
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)

        repository.deleteRule(repository.getById(ruleId)!!)

        assertNull(repository.getById(ruleId))
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
    }
}
