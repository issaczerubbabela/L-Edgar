package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseTableTriggers
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

/** The Ledger's batch changes, run against real Room queries and the version trigger. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BatchUpdateQueriesTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var dao: ExpenseDao
    private var hdfc = 0L
    private var wallet = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = ExpenseTableTriggers.install(db)
            })
            .allowMainThreadQueries()
            .build()
        dao = db.expenseDao()
        hdfc = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC", initialBalance = 0.0))
        wallet = db.accountDao().insert(AccountRecord(groupName = "Cash", accountName = "Wallet", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertSynced(type: String, category: String = "", from: Long? = null, to: Long? = null, account: Long? = null): Long {
        val id = dao.insert(ExpenseRecord(date = "2026-10-09", type = type, category = category, description = "", amount = 100.0,
            accountId = account, remarks = "", fromAccountId = from, toAccountId = to))
        dao.markSyncedIfUnchanged(id, dao.getById(id)!!.localVersion, "sheet-rev")
        return id
    }

    @Test
    fun `a batch category change never reaches a Transfer, a Balance adjustment or the other type`() = runBlocking {
        val expense = insertSynced("Expense", "Food", account = hdfc)
        val income = insertSynced("Income", "Refund", account = hdfc)
        val transfer = insertSynced("Transfer", from = hdfc, to = wallet)
        val adjustment = insertSynced("Adjustment", account = wallet)
        val all = listOf(expense, income, transfer, adjustment)

        dao.updateTransactionsCategoryByIds(all, newCategory = "Shopping", type = "Expense")

        assertEquals("Shopping", dao.getById(expense)!!.category)
        assertEquals("Refund", dao.getById(income)!!.category)
        assertEquals("", dao.getById(transfer)!!.category)
        assertEquals("", dao.getById(adjustment)!!.category)
        listOf(income, transfer, adjustment).forEach { assertTrue("untouched row stays synced", dao.getById(it)!!.isSynced) }
    }

    @Test
    fun `a batch account change leaves Transfers alone`() = runBlocking {
        val transfer = insertSynced("Transfer", from = hdfc, to = wallet)

        dao.updateTransactionsAssetByIds(listOf(transfer), accountId = wallet)

        val after = dao.getById(transfer)!!
        assertEquals(hdfc, after.fromAccountId)
        assertEquals(wallet, after.toAccountId)
        assertTrue(after.isSynced)
    }

    @Test
    fun `every batch change leaves its rows unsynced with a raised version`() = runBlocking {
        val changes: List<Pair<String, suspend (Long) -> Unit>> = listOf(
            "date" to { id -> dao.updateTransactionsDateByIds(listOf(id), "2026-10-01") },
            "category" to { id -> dao.updateTransactionsCategoryByIds(listOf(id), "Shopping", "Expense") },
            "account" to { id -> dao.updateTransactionsAssetByIds(listOf(id), wallet) },
            "description" to { id -> dao.updateTransactionsDescriptionByIds(listOf(id), "Groceries") }
        )
        for ((name, change) in changes) {
            val id = insertSynced("Expense", "Food", account = hdfc)
            val before = dao.getById(id)!!.localVersion

            change(id)

            val after = dao.getById(id)!!
            assertFalse("$name leaves the row unsynced", after.isSynced)
            assertEquals("$name marks it for upload", "UPDATE", after.syncAction)
            assertTrue("$name raises the version", after.localVersion > before)
        }
    }
}
