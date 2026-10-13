package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseTableTriggers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [ExpenseDao.lastExpenseAccountId], behind Quick Add's default Account (issue #81). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LastExpenseAccountQueryTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var dao: ExpenseDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = ExpenseTableTriggers.install(db)
            })
            .allowMainThreadQueries()
            .build()
        dao = db.expenseDao()
        accountDao = db.accountDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `with no expenses there is no account to remember`() = runBlocking {
        assertNull(dao.lastExpenseAccountId())
    }

    @Test
    fun `the most recently dated expense's account wins`() = runBlocking {
        val cash = accountDao.insert(account("Cash"))
        val hdfc = accountDao.insert(account("HDFC"))
        dao.insert(expense(accountId = cash, date = "2026-10-01"))
        dao.insert(expense(accountId = hdfc, date = "2026-10-05"))

        assertEquals(hdfc, dao.lastExpenseAccountId())
    }

    @Test
    fun `on the same day the most recently inserted expense wins`() = runBlocking {
        val cash = accountDao.insert(account("Cash"))
        val hdfc = accountDao.insert(account("HDFC"))
        dao.insert(expense(accountId = cash, date = "2026-10-05"))
        dao.insert(expense(accountId = hdfc, date = "2026-10-05"))

        assertEquals(hdfc, dao.lastExpenseAccountId())
    }

    @Test
    fun `an expense with no account is skipped`() = runBlocking {
        val cash = accountDao.insert(account("Cash"))
        dao.insert(expense(accountId = cash, date = "2026-10-01"))
        dao.insert(expense(accountId = null, date = "2026-10-05"))

        assertEquals(cash, dao.lastExpenseAccountId())
    }

    @Test
    fun `a soft-deleted expense is skipped`() = runBlocking {
        val cash = accountDao.insert(account("Cash"))
        val hdfc = accountDao.insert(account("HDFC"))
        dao.insert(expense(accountId = cash, date = "2026-10-01"))
        dao.insert(expense(accountId = hdfc, date = "2026-10-05", syncAction = "DELETE"))

        assertEquals(cash, dao.lastExpenseAccountId())
    }

    @Test
    fun `income and transfers don't count, only expenses`() = runBlocking {
        val cash = accountDao.insert(account("Cash"))
        val hdfc = accountDao.insert(account("HDFC"))
        dao.insert(expense(accountId = cash, date = "2026-10-01"))
        dao.insert(expense(accountId = hdfc, date = "2026-10-05", type = "Income"))
        dao.insert(ExpenseRecord(date = "2026-10-06", type = "Transfer", category = "Transfer", description = "", amount = 1.0, fromAccountId = cash, toAccountId = hdfc, remarks = "", isSynced = false, syncAction = "INSERT"))

        assertEquals(cash, dao.lastExpenseAccountId())
    }

    private fun account(name: String) = AccountRecord(groupName = "Accounts", accountName = name, initialBalance = 0.0)

    private fun expense(
        accountId: Long?,
        date: String,
        type: String = "Expense",
        syncAction: String = "INSERT"
    ) = ExpenseRecord(
        date = date,
        type = type,
        category = "Food",
        description = "Lunch",
        amount = 50.0,
        accountId = accountId,
        remarks = "",
        isSynced = false,
        syncAction = syncAction
    )
}
