package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.dao.DropdownUsage
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real count queries behind the confirm before deleting a Dropdown option. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DropdownUsageQueriesTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repository: DropdownOptionRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DropdownOptionRepositoryImpl(db.dropdownOptionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `an expense Category counts its own type's uses, ignoring case, and only the running cycle's routes`() = runBlocking {
        transaction("Expense", "Food")
        transaction("Expense", "food")
        transaction("Expense", "Food", syncAction = "DELETE")
        transaction("Income", "Food")
        transaction("Expense", "Fuel")
        route(running = true, category = "FOOD")
        route(running = false, category = "Food")
        merchantRule("swiggy", "Expense", "Food", MerchantRuleOrigin.USER)
        merchantRule("zomato", "Expense", "food", MerchantRuleOrigin.LEARNED)
        merchantRule("dunzo", "Expense", "Food", MerchantRuleOrigin.CANDIDATE)
        merchantRule("refunds", "Income", "Food", MerchantRuleOrigin.USER)
        repeatRule("Expense", "Food")
        repeatRule("Income", "Food")

        val usage = repository.usage(option("EXPENSE_CATEGORY", "Food"))

        assertEquals(DropdownUsage(transactions = 2, bucketRoutes = 1, merchantRules = 2, repeatRules = 1), usage)
    }

    @Test
    fun `an income Category that shares a name with an expense one counts only income uses and no routes`() = runBlocking {
        transaction("Expense", "Food")
        transaction("Income", "Food")
        route(running = true, category = "Food")
        merchantRule("refunds", "Income", "Food", MerchantRuleOrigin.USER)
        repeatRule("Income", "Food")

        val usage = repository.usage(option("INCOME_CATEGORY", "Food"))

        assertEquals(DropdownUsage(transactions = 1, merchantRules = 1, repeatRules = 1), usage)
    }

    @Test
    fun `an Account group counts the Accounts in it, hidden ones too`() = runBlocking {
        db.accountDao().insert(AccountRecord(groupName = "Cards", accountName = "HDFC Card", initialBalance = 0.0))
        db.accountDao().insert(AccountRecord(groupName = "cards", accountName = "Old card", initialBalance = 0.0, isHidden = true))
        db.accountDao().insert(AccountRecord(groupName = "Cash", accountName = "Wallet", initialBalance = 0.0))

        assertEquals(DropdownUsage(accounts = 2), repository.usage(option("ACCOUNT_GROUP", "Cards")))
    }

    @Test
    fun `a Category nothing uses, and any Payment mode, are unused`() = runBlocking {
        transaction("Expense", "Food")

        assertTrue(repository.usage(option("EXPENSE_CATEGORY", "Travel")).isUnused)
        assertTrue(repository.usage(option("PAYMENT_MODE", "UPI")).isUnused)
    }

    private fun option(type: String, name: String) = DropdownOption(optionType = type, name = name, displayOrder = 0)

    private suspend fun transaction(type: String, category: String, syncAction: String = "INSERT") {
        db.expenseDao().insert(
            ExpenseRecord(
                date = "2026-11-20", type = type, category = category, description = "x",
                amount = 10.0, remarks = "", syncAction = syncAction
            )
        )
    }

    private suspend fun route(running: Boolean, category: String) {
        val dao = db.bucketBudgetDao()
        val cycleId = dao.insertCycle(
            BudgetCycle(
                startDate = if (running) "2026-11-01" else "2026-10-01",
                endDate = if (running) "2026-11-30" else "2026-10-31",
                spendableAmount = 1000.0,
                closedAt = if (running) null else "2026-11-01"
            )
        )
        val bucketId = dao.insertBucket(BudgetBucket(cycleId = cycleId, name = "Needs", colorIndex = 0, allocatedAmount = 500.0))
        dao.assignCategory(BucketCategory(cycleId = cycleId, bucketId = bucketId, category = category))
    }

    private suspend fun merchantRule(merchant: String, type: String, category: String, origin: String) {
        db.merchantRuleDao().upsert(
            MerchantRule(
                merchantNorm = merchant, direction = if (type == "Income") "CREDIT" else "DEBIT",
                category = category, type = type, origin = origin, updatedAt = 0
            )
        )
    }

    private suspend fun repeatRule(type: String, category: String) {
        db.recurringRuleDao().insert(
            RecurringRule(
                type = type, category = category, description = "x", amount = 10.0, remarks = "",
                frequency = "MONTHLY", anchorDay = 1, startDate = "2026-11-01", nextDate = "2026-12-01",
                createdAt = "2026-11-01"
            )
        )
    }
}
