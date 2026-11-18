package com.issaczerubbabel.ledgar.sync

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseTableTriggers
import com.issaczerubbabel.ledgar.data.remote.AccountImportDto
import com.issaczerubbabel.ledgar.data.remote.DropdownImportDto
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SheetListsRestorerTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var restorer: SheetListsRestorer

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = ExpenseTableTriggers.install(db)
            })
            .allowMainThreadQueries()
            .build()
        restorer = SheetListsRestorer(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `Import keeps every Stats role the Sheet has`() = runBlocking {
        db.dropdownOptionDao().insert(DropdownOption(optionType = "EXPENSE_CATEGORY", name = "Old", displayOrder = 0))

        val restored = restorer.restoreDropdowns(
            listOf(
                DropdownImportDto(optionType = "EXPENSE_CATEGORY", name = "Food & Dining", displayOrder = 0),
                DropdownImportDto(optionType = "EXPENSE_CATEGORY", name = "Investments/Savings", displayOrder = 1, role = "SAVING"),
                DropdownImportDto(optionType = "INCOME_CATEGORY", name = "Refunds & Paybacks", displayOrder = 0, role = "REFUND"),
                DropdownImportDto(optionType = "ACCOUNT_GROUP", name = "Savings", displayOrder = 0, role = "SAVINGS"),
                DropdownImportDto(optionType = "ACCOUNT_GROUP", name = "Credit card", displayOrder = 1, role = " LIABILITY "),
                DropdownImportDto(optionType = "PAYMENT_MODE", name = "UPI", displayOrder = 0)
            )
        )

        assertEquals(5, restored)
        assertEquals(
            mapOf(
                "Food & Dining" to "",
                "Investments/Savings" to "SAVING",
                "Refunds & Paybacks" to "REFUND",
                "Savings" to "SAVINGS",
                "Credit card" to "LIABILITY"
            ),
            db.dropdownOptionDao().getAllOptionsSnapshot().associate { it.name to it.role }
        )
    }

    @Test
    fun `Import keeps every Transaction on its Account`() = runBlocking {
        val accounts = db.accountDao()
        val hdfc = accounts.insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
        val oldCard = accounts.insert(AccountRecord(groupName = "Card", accountName = "Old card", initialBalance = 0.0))
        // Logged on the phone: linked by id only, with no names stored.
        val lunch = db.expenseDao().insert(expense(accountId = hdfc))
        val cardBill = db.expenseDao().insert(transfer(fromId = hdfc, toId = oldCard))
        // Pulled before its Account existed: no id, only the Sheet's name.
        val unlinked = db.expenseDao().insert(expense(accountId = null, accountName = "City Union Bank"))

        val restored = restorer.restoreAccounts(
            listOf(
                AccountImportDto(groupName = "Bank", accountName = "hdfc savings", initialBalance = 79110.0, displayOrder = 0),
                AccountImportDto(groupName = "Bank", accountName = "City Union Bank", initialBalance = 0.0, displayOrder = 1)
            )
        )

        assertEquals(2, restored)
        val byName = accounts.getAllAccountsSnapshot().associateBy { it.accountName }
        assertEquals("an Account the Sheet also has keeps its id", hdfc, byName.getValue("hdfc savings").id)
        assertEquals(79110.0, byName.getValue("hdfc savings").initialBalance, 0.0)
        assertTrue("an Account the Sheet lacks is deleted", "Old card" !in byName)

        val expenses = db.expenseDao()
        assertEquals(hdfc, expenses.getById(lunch)!!.accountId)
        assertEquals(hdfc, expenses.getById(lunch)!!.fromAccountId)
        val bill = expenses.getById(cardBill)!!
        assertEquals(hdfc, bill.fromAccountId)
        assertNull(bill.toAccountId)
        assertEquals("a deleted Account's name stays on its Transactions, so it can be linked again", "Old card", bill.toAccountName)
        val cub = expenses.getById(unlinked)!!
        assertEquals("a name that now matches an Account is linked", byName.getValue("City Union Bank").id, cub.accountId)
        assertEquals(cub.accountId, cub.fromAccountId)
        assertTrue("linking doesn't change what the Sheet holds, so nothing waits to sync", cub.isSynced)
    }

    @Test
    fun `a Sheet with no Accounts leaves the phone's alone`() = runBlocking {
        val wallet = db.accountDao().insert(AccountRecord(groupName = "Cash", accountName = "Wallet", initialBalance = 0.0))
        db.expenseDao().insert(expense(accountId = wallet))

        assertEquals(0, restorer.restoreAccounts(emptyList()))

        assertEquals(listOf(wallet), db.accountDao().getAllAccountsSnapshot().map { it.id })
        assertEquals(wallet, db.expenseDao().getAllRecordsSnapshot().single().accountId)
    }

    private fun expense(accountId: Long?, accountName: String? = null) = ExpenseRecord(
        date = "2026-11-01", type = "Expense", category = "Food & Dining", description = "Lunch", amount = 250.0,
        accountId = accountId, fromAccountId = accountId, accountName = accountName, fromAccountName = accountName,
        remarks = "", isSynced = true, syncAction = "NONE"
    )

    private fun transfer(fromId: Long, toId: Long) = ExpenseRecord(
        date = "2026-11-02", type = "Transfer", category = "Transfer", description = "Card bill", amount = 5000.0,
        accountId = null, fromAccountId = fromId, toAccountId = toId, remarks = "", isSynced = true, syncAction = "NONE"
    )
}
