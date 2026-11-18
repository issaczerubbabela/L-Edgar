package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.bucket.StartCycleRequest
import com.issaczerubbabel.ledgar.data.bucket.StartCycleResult
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseTableTriggers
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DropdownEditorTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var editor: DropdownEditor

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = ExpenseTableTriggers.install(db)
            })
            .allowMainThreadQueries()
            .build()
        editor = DropdownEditor(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `renaming an expense Category renames it everywhere and re-sends its Transactions`() = runBlocking {
        val option = option("EXPENSE_CATEGORY", "Food")
        option("INCOME_CATEGORY", "Food") // same name, other type: untouched
        val dao = db.expenseDao()
        val lunch = synced(expense("Expense", "food"))
        val refund = synced(expense("Income", "Food"))
        val closed = db.bucketBudgetDao().insertCycle(BudgetCycle(startDate = "2026-09-25", endDate = "2026-10-24", spendableAmount = 1.0, closedAt = "2026-10-25"))
        val running = db.bucketBudgetDao().insertCycle(BudgetCycle(startDate = "2026-10-25", endDate = "2026-11-24", spendableAmount = 1.0))
        listOf(closed, running).forEach { cycle ->
            val bucket = db.bucketBudgetDao().insertBucket(BudgetBucket(cycleId = cycle, name = "Essentials", colorIndex = 0, allocatedAmount = 1.0))
            db.bucketBudgetDao().assignCategory(BucketCategory(cycleId = cycle, bucketId = bucket, category = "Food"))
        }
        db.budgetDao().upsert(Budget(monthYear = "2026-10", category = "Food", amount = 5000.0))
        db.merchantRuleDao().upsert(MerchantRule(merchantNorm = "swiggy", category = "Food", type = "Expense", origin = "USER", updatedAt = 0))
        db.recurringRuleDao().insert(
            RecurringRule(type = "Expense", category = "Food", description = "", amount = 1.0, remarks = "", frequency = "MONTHLY",
                anchorDay = 1, startDate = "2026-11-01", nextDate = "2026-12-01", createdAt = "2026-11-01")
        )

        val result = editor.rename(option, "  Food & Dining ")

        assertEquals(DropdownEditor.RenameResult.Renamed(1), result)
        assertEquals("Food & Dining", db.dropdownOptionDao().getAllOptionsSnapshot().first { it.id == option.id }.name)
        dao.getById(lunch)!!.let {
            assertEquals("Food & Dining", it.category)
            assertFalse("it waits to sync, so the Sheet shows the new name", it.isSynced)
            assertEquals("UPDATE", it.syncAction)
        }
        assertEquals("an Income with the same Category name is another Category", "Food", dao.getById(refund)!!.category)
        assertTrue(dao.getById(refund)!!.isSynced)
        assertEquals(
            "both cycles keep the Category in its Bucket",
            listOf("Food & Dining", "Food & Dining"),
            db.bucketBudgetDao().getAllAssignments().map { it.category }
        )
        assertEquals(listOf("Food & Dining"), db.budgetDao().getAllBudgetsSnapshot().map { it.category })
        assertEquals("Food & Dining", db.merchantRuleDao().getByMerchant("swiggy")!!.category)
        assertEquals("Food & Dining", db.recurringRuleDao().getById(1)!!.category)
    }

    @Test
    fun `a name another option of the same type has is refused`() = runBlocking {
        val food = option("EXPENSE_CATEGORY", "Food")
        option("EXPENSE_CATEGORY", "Groceries")

        assertEquals(DropdownEditor.RenameResult.NameTaken("Groceries"), editor.rename(food, "groceries"))
        assertEquals(DropdownEditor.RenameResult.Blank, editor.rename(food, "  "))
        assertEquals("a change of case is fine", DropdownEditor.RenameResult.Renamed(0), editor.rename(food, "FOOD"))
    }

    @Test
    fun `renaming an Account group moves its Accounts`() = runBlocking {
        val group = option("ACCOUNT_GROUP", "Bank")
        db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC", initialBalance = 0.0))

        editor.rename(group, "Bank accounts")

        assertEquals("Bank accounts", db.accountDao().getAllAccountsSnapshot().single().groupName)
    }

    @Test
    fun `deleting an expense Category takes it out of the running cycle's Buckets only`() = runBlocking {
        val travel = option("EXPENSE_CATEGORY", "Travel")
        option("EXPENSE_CATEGORY", "Rent")
        val closed = db.bucketBudgetDao().insertCycle(BudgetCycle(startDate = "2026-09-25", endDate = "2026-10-24", spendableAmount = 1.0, closedAt = "2026-10-25"))
        val running = db.bucketBudgetDao().insertCycle(BudgetCycle(startDate = "2026-10-25", endDate = "2026-11-24", spendableAmount = 1.0))
        listOf(closed, running).forEach { cycle ->
            val bucket = db.bucketBudgetDao().insertBucket(BudgetBucket(cycleId = cycle, name = "Fun", colorIndex = 0, allocatedAmount = 1.0))
            db.bucketBudgetDao().assignCategory(BucketCategory(cycleId = cycle, bucketId = bucket, category = "Travel"))
            db.bucketBudgetDao().assignCategory(BucketCategory(cycleId = cycle, bucketId = bucket, category = "Rent"))
            // Deleted before this change, but still routed.
            db.bucketBudgetDao().assignCategory(BucketCategory(cycleId = cycle, bucketId = bucket, category = "Investments/Savings"))
        }

        editor.delete(travel)

        assertEquals(listOf("Rent"), db.bucketBudgetDao().getCategoryAssignments(running).map { it.category })
        assertEquals(3, db.bucketBudgetDao().getCategoryAssignments(closed).size)
    }

    @Test
    fun `a new cycle doesn't carry deleted Categories into its Buckets`() = runBlocking {
        option("EXPENSE_CATEGORY", "Rent")
        val running = db.bucketBudgetDao().insertCycle(BudgetCycle(startDate = "2026-10-25", endDate = "2026-11-24", spendableAmount = 1.0))
        val bucket = db.bucketBudgetDao().insertBucket(BudgetBucket(cycleId = running, name = "Essentials", colorIndex = 0, allocatedAmount = 1.0))
        db.bucketBudgetDao().assignCategory(BucketCategory(cycleId = running, bucketId = bucket, category = "Rent"))
        db.bucketBudgetDao().assignCategory(BucketCategory(cycleId = running, bucketId = bucket, category = "Investments/Savings"))

        val started = BucketBudgetRepositoryImpl(db, db.bucketBudgetDao()).startCycle(
            StartCycleRequest(
                startDate = LocalDate.parse("2026-11-25"),
                endDate = LocalDate.parse("2026-12-24"),
                spendableAmount = 1.0,
                carryOverBuckets = true
            ),
            today = LocalDate.parse("2026-11-25")
        ) as StartCycleResult.Started

        assertEquals(listOf("Rent"), db.bucketBudgetDao().getCategoryAssignments(started.cycleId).map { it.category })
    }

    private suspend fun option(type: String, name: String): DropdownOption {
        val option = DropdownOption(optionType = type, name = name, displayOrder = 0)
        return option.copy(id = db.dropdownOptionDao().insert(option))
    }

    private suspend fun synced(record: ExpenseRecord): Long {
        val dao = db.expenseDao()
        val id = dao.insert(record)
        dao.markSyncedIfUnchanged(id, dao.getById(id)!!.localVersion, "rev")
        return id
    }

    private fun expense(type: String, category: String) = ExpenseRecord(
        date = "2026-11-01", type = type, category = category, description = "", amount = 100.0, accountId = null, remarks = ""
    )
}
