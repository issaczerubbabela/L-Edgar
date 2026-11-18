package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.UnmatchedAccountName
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseTableTriggers
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.sync.SheetTransactionMapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sheet account names that match no Account: kept, listed, and linked once an Account fits. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AccountNameLinkingTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var accounts: AccountRepositoryImpl
    private var cash = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = ExpenseTableTriggers.install(db)
            })
            .allowMainThreadQueries()
            .build()
        accounts = AccountRepositoryImpl(db.accountDao(), db.expenseDao(), db.dropdownOptionDao(), db)
        cash = db.accountDao().insert(AccountRecord(groupName = "Cash", accountName = "Cash", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a Sheet row naming an unknown Account stays unlinked instead of going on the fallback`() = runBlocking {
        val mapper = SheetTransactionMapper(db.accountDao())
        val context = mapper.resolveAccountContext()

        val named = mapper.mapImportRecord(sheetRow("City Union Bank"), context.accountsByName, context.accountsByGroup, context.fallbackAccountId)
        assertFalse(named.discarded)
        assertNull(named.record.accountId)
        assertEquals("City Union Bank", named.record.accountName)

        val unnamed = mapper.mapImportRecord(sheetRow(""), context.accountsByName, context.accountsByGroup, context.fallbackAccountId)
        assertEquals("a row with no Account at all still goes on the fallback", cash, unnamed.record.accountId)
    }

    @Test
    fun `unknown names are listed, including rows an older version guessed onto the fallback`() = runBlocking {
        insert(expense(accountId = null, name = "City Union Bank"))
        insert(expense(accountId = null, name = "city union bank "))
        insert(expense(accountId = cash, name = "UPI")) // the old fallback
        insert(expense(accountId = cash, name = "Cash")) // on the Account it names: fine
        insert(transfer(fromName = "City Union Bank", toName = "Bank Transfer"))

        assertEquals(
            listOf(
                UnmatchedAccountName("City Union Bank", null, 3),
                UnmatchedAccountName("Bank Transfer", null, 1),
                UnmatchedAccountName("UPI", cash, 1)
            ),
            accounts.observeUnmatchedAccountNames().first()
        )
    }

    @Test
    fun `creating an Account links the Transactions that name it, without re-sending them`() = runBlocking {
        val lunch = insert(expense(accountId = null, name = "City Union Bank"))
        val guessed = insert(expense(accountId = cash, name = "City Union Bank"))
        val rent = insert(transfer(fromName = "City Union Bank", toName = "Landlord"))

        val cub = accounts.save(AccountRecord(groupName = "Bank", accountName = "City Union Bank", initialBalance = 0.0))

        val dao = db.expenseDao()
        assertEquals(cub, dao.getById(lunch)!!.accountId)
        assertEquals(cub, dao.getById(lunch)!!.fromAccountId)
        assertTrue(dao.getById(lunch)!!.isSynced)
        assertEquals(cub, dao.getById(rent)!!.fromAccountId)
        assertNull(dao.getById(rent)!!.toAccountId)
        assertEquals("a row already on an Account is left for the next Pull or the Account names screen", cash, dao.getById(guessed)!!.accountId)
    }

    @Test
    fun `mapping a name to another Account moves its Transactions and sends them to the Sheet`() = runBlocking {
        val hdfc = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC", initialBalance = 0.0))
        val unlinked = insert(expense(accountId = null, name = "UPI"))
        val guessed = insert(expense(accountId = cash, name = "UPI"))
        val transfer = insert(transfer(fromName = "UPI", toName = "HDFC"))

        assertEquals(3, accounts.mapAccountName("upi", hdfc))

        val dao = db.expenseDao()
        listOf(unlinked, guessed).forEach { id ->
            val row = dao.getById(id)!!
            assertEquals(hdfc, row.accountId)
            assertEquals("HDFC", row.accountName)
            assertFalse("the Sheet still says UPI, so it waits to sync", row.isSynced)
            assertEquals("UPDATE", row.syncAction)
        }
        assertEquals(hdfc, dao.getById(transfer)!!.fromAccountId)
        assertTrue(accounts.observeUnmatchedAccountNames().first().none { it.name.equals("UPI", ignoreCase = true) })
    }

    @Test
    fun `creating an Account for a name puts its Transactions on it`() = runBlocking {
        val guessed = insert(expense(accountId = cash, name = "City Union Bank"))

        val cub = accounts.createAccountForName("City Union Bank", "Bank")

        val row = db.expenseDao().getById(guessed)!!
        assertEquals(cub, row.accountId)
        assertTrue("the Sheet already says City Union Bank", row.isSynced)
        assertTrue(accounts.observeUnmatchedAccountNames().first().isEmpty())
    }

    @Test
    fun `renaming an Account sends its Transactions to the Sheet and links rows with the new name`() = runBlocking {
        val upi = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "UPI", initialBalance = 0.0))
        val onUpi = insert(expense(accountId = upi, name = null))
        val waiting = insert(expense(accountId = null, name = "City Union Bank"))

        accounts.save(db.accountDao().getAccountById(upi)!!.copy(accountName = "City Union Bank"))

        val dao = db.expenseDao()
        assertFalse(dao.getById(onUpi)!!.isSynced)
        assertEquals("UPDATE", dao.getById(onUpi)!!.syncAction)
        assertEquals(upi, dao.getById(waiting)!!.accountId)
    }

    @Test
    fun `editing an Account without renaming it re-sends nothing`() = runBlocking {
        val onCash = insert(expense(accountId = cash, name = null))

        accounts.save(db.accountDao().getAccountById(cash)!!.copy(initialBalance = 500.0))

        assertTrue(db.expenseDao().getById(onCash)!!.isSynced)
    }

    private suspend fun insert(record: ExpenseRecord): Long {
        val dao = db.expenseDao()
        val id = dao.insert(record)
        dao.markSyncedIfUnchanged(id, dao.getById(id)!!.localVersion, "rev")
        return id
    }

    private fun expense(accountId: Long?, name: String?) = ExpenseRecord(
        date = "2026-11-01", type = "Expense", category = "Groceries", description = "", amount = 100.0,
        accountId = accountId, fromAccountId = accountId, accountName = name, fromAccountName = name,
        remarks = "", isSynced = true, syncAction = "NONE"
    )

    private fun transfer(fromName: String, toName: String) = ExpenseRecord(
        date = "2026-11-02", type = "Transfer", category = "Transfer", description = "", amount = 100.0,
        accountId = null, fromAccountName = fromName, toAccountName = toName,
        remarks = "", isSynced = true, syncAction = "NONE"
    )

    private fun sheetRow(accountName: String) = ImportRecordDto(
        timestamp = "11/1/2026 08:00:00", date = "2026-11-01", type = "Expense", expCategory = "Groceries",
        description = "", amount = 100.0, accountName = accountName, remarks = ""
    )
}
