package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseTableTriggers
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.data.remote.SheetTransactionDto
import com.issaczerubbabel.ledgar.sync.SheetTransactionMapper
import com.issaczerubbabel.ledgar.sync.toSheetDto
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

/** Renaming an Account re-sends its Transactions, so the Sheet and a later Import keep them on it (#152). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AccountRenameResyncTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repository: AccountRepositoryImpl
    private var cashId = 0L
    private var bankId = 0L
    private var otherId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = ExpenseTableTriggers.install(db)
            })
            .allowMainThreadQueries()
            .build()
        repository = AccountRepositoryImpl(db.accountDao(), db.expenseDao(), db.dropdownOptionDao(), db)
        cashId = db.accountDao().insert(AccountRecord(groupName = "Cash", accountName = "Cash", initialBalance = 0.0))
        bankId = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "City Union Bank", initialBalance = 0.0))
        otherId = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "Savings", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun syncedRow(
        syncId: String,
        type: String = "Expense",
        accountId: Long? = null,
        fromAccountId: Long? = null,
        toAccountId: Long? = null,
        syncAction: String = "NONE",
        isSynced: Boolean = true
    ): Long = db.expenseDao().insert(
        ExpenseRecord(
            date = "2026-11-01",
            type = type,
            category = if (type == "Transfer") "Transfer" else "Food",
            description = "row $syncId",
            amount = 100.0,
            accountId = accountId,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            remarks = "",
            isSynced = isSynced,
            syncAction = syncAction,
            syncId = syncId,
            syncedRevision = "rev-$syncId"
        )
    )

    private suspend fun row(id: Long) = db.expenseDao().getAllRecordsSnapshot().single { it.id == id }

    @Test
    fun `renaming an Account marks every Transaction on any of its sides as an unsynced update`() = runBlocking {
        val expense = syncedRow("e", accountId = bankId, fromAccountId = bankId)
        val income = syncedRow("i", type = "Income", accountId = bankId, toAccountId = bankId)
        val transferOut = syncedRow("t1", type = "Transfer", fromAccountId = bankId, toAccountId = otherId)
        val transferIn = syncedRow("t2", type = "Transfer", fromAccountId = otherId, toAccountId = bankId)
        val elsewhere = syncedRow("x", accountId = otherId, fromAccountId = otherId)
        val versionsBefore = listOf(expense, income, transferOut, transferIn).associateWith { row(it).localVersion }

        repository.save(repository.getAccountById(bankId)!!.copy(accountName = "Ministry Fund"))

        listOf(expense, income, transferOut, transferIn).forEach { id ->
            val record = row(id)
            assertFalse("row ${record.syncId} should be unsynced", record.isSynced)
            assertEquals("UPDATE", record.syncAction)
            assertTrue("the trigger raises localVersion", record.localVersion > versionsBefore.getValue(id))
            assertEquals("rev-${record.syncId}", record.syncedRevision)
        }
        assertTrue(row(elsewhere).isSynced)
        assertEquals("NONE", row(elsewhere).syncAction)
    }

    @Test
    fun `a rename keeps pending inserts and deletes as they are`() = runBlocking {
        val pendingInsert = syncedRow("p", accountId = bankId, syncAction = "INSERT", isSynced = false)
        val pendingDelete = syncedRow("d", accountId = bankId, syncAction = "DELETE", isSynced = false)

        repository.save(repository.getAccountById(bankId)!!.copy(accountName = "Ministry Fund"))

        assertEquals("INSERT", row(pendingInsert).syncAction)
        assertEquals("DELETE", row(pendingDelete).syncAction)
    }

    @Test
    fun `saving an Account without changing its name marks nothing`() = runBlocking {
        val expense = syncedRow("e", accountId = bankId, fromAccountId = bankId)
        val versionBefore = row(expense).localVersion

        repository.save(repository.getAccountById(bankId)!!.copy(description = "Salary account", isHidden = true))

        assertTrue(row(expense).isSynced)
        assertEquals("NONE", row(expense).syncAction)
        assertEquals(versionBefore, row(expense).localVersion)
    }

    @Test
    fun `re-sending an Account marks its Transactions without a rename`() = runBlocking {
        val expense = syncedRow("e", accountId = bankId, fromAccountId = bankId)
        val transfer = syncedRow("t", type = "Transfer", fromAccountId = otherId, toAccountId = bankId)
        val elsewhere = syncedRow("x", accountId = otherId, fromAccountId = otherId)

        val marked = repository.resendTransactions(bankId)

        assertEquals(2, marked)
        assertFalse(row(expense).isSynced)
        assertFalse(row(transfer).isSynced)
        assertEquals("UPDATE", row(transfer).syncAction)
        assertTrue(row(elsewhere).isSynced)
    }

    @Test
    fun `after a rename the Sheet rows carry the new name and a fresh Import puts them back on that Account`() = runBlocking {
        val expense = syncedRow("e", accountId = bankId, fromAccountId = bankId)
        val transfer = syncedRow("t", type = "Transfer", fromAccountId = bankId, toAccountId = otherId)
        repository.save(repository.getAccountById(bankId)!!.copy(accountName = "Ministry Fund"))

        val names = db.accountDao().getAllAccountsSnapshot().associate { it.id to it.accountName }
        val sent = listOf(row(expense), row(transfer)).map { it.toSheetDto(names) }
        assertEquals("Ministry Fund", sent[0].accountName)
        assertEquals("Ministry Fund", sent[1].fromAccountName)

        // A fresh Import reads those rows back against the same Accounts.
        val mapper = SheetTransactionMapper(db.accountDao())
        val context = mapper.resolveAccountContext()
        val imported = sent.map {
            mapper.mapImportRecord(it.asPulled(), context.accountsByName, context.accountsByGroup, context.fallbackAccountId).record
        }

        assertEquals(bankId, imported[0].accountId)
        assertEquals(bankId, imported[0].fromAccountId)
        assertNull(imported[1].accountId)
        assertEquals(bankId, imported[1].fromAccountId)
        assertEquals(otherId, imported[1].toAccountId)
    }

    @Test
    fun `a Sheet row still under the old name falls back to Cash, which is what the re-send repairs`() = runBlocking {
        val expense = syncedRow("e", accountId = bankId, fromAccountId = bankId)
        val stale = row(expense).toSheetDto(mapOf(bankId to "City Union Bank"))
        repository.save(repository.getAccountById(bankId)!!.copy(accountName = "Ministry Fund"))

        val mapper = SheetTransactionMapper(db.accountDao())
        val context = mapper.resolveAccountContext()
        val imported = mapper.mapImportRecord(
            stale.asPulled(), context.accountsByName, context.accountsByGroup, context.fallbackAccountId
        ).record

        assertEquals(cashId, imported.accountId)
    }

    private fun SheetTransactionDto.asPulled() = ImportRecordDto(
        id = id,
        timestamp = timestamp,
        date = date,
        type = type,
        expCategory = expCategory,
        incCategory = incCategory,
        description = description,
        amount = amount,
        accountName = accountName,
        fromAccountName = fromAccountName,
        toAccountName = toAccountName,
        remarks = remarks,
        isBookmarked = isBookmarked
    )
}
