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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real Room queries and version trigger that Transaction Sync relies on. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ExpenseSyncQueriesTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var dao: ExpenseDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = ExpenseTableTriggers.install(db)
            })
            .allowMainThreadQueries()
            .build()
        dao = db.expenseDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `every change raises the version, even a whole-row update built with a stale version`() = runBlocking {
        val id = dao.insert(transaction())
        dao.updateTransactionsDescriptionByIds(listOf(id), "Dinner")
        dao.updateTransactionsDescriptionByIds(listOf(id), "Supper")
        val beforeStaleWrite = dao.getById(id)!!.localVersion

        dao.update(dao.getById(id)!!.copy(amount = 99.0, localVersion = 0))

        assertEquals(2L, beforeStaleWrite)
        assertTrue(dao.getById(id)!!.localVersion > beforeStaleWrite)
    }

    @Test
    fun `a Transaction settles only if it has not changed since Sync read it`() = runBlocking {
        val id = dao.insert(transaction())
        val sentVersion = dao.getById(id)!!.localVersion

        dao.update(dao.getById(id)!!.copy(amount = 75.0))
        assertEquals(0, dao.markSyncedIfUnchanged(id, sentVersion, FINGERPRINT))
        assertFalse(dao.getById(id)!!.isSynced)

        assertEquals(1, dao.markSyncedIfUnchanged(id, dao.getById(id)!!.localVersion, FINGERPRINT))
        assertTrue(dao.getById(id)!!.isSynced)
        assertEquals("NONE", dao.getById(id)!!.syncAction)
        assertEquals(FINGERPRINT, dao.getById(id)!!.syncedRevision)
    }

    @Test
    fun `settling never undoes a pending delete`() = runBlocking {
        val id = dao.insert(transaction())
        dao.markTransactionDeletedById(id)

        assertEquals(0, dao.markSyncedIfUnchanged(id, dao.getById(id)!!.localVersion, FINGERPRINT))
        assertEquals("DELETE", dao.getById(id)!!.syncAction)
    }

    @Test
    fun `a synced delete removes the row only if it is still the same delete`() = runBlocking {
        val id = dao.insert(transaction())
        dao.markTransactionDeletedById(id)
        val version = dao.getById(id)!!.localVersion

        assertEquals(0, dao.finishDeleteIfUnchanged(id, version - 1))
        assertNotNull(dao.getById(id))
        assertEquals(1, dao.finishDeleteIfUnchanged(id, version))
        assertNull(dao.getById(id))
    }

    @Test
    fun `an edit saved from an earlier read keeps the ID Sync linked meanwhile`() = runBlocking {
        val id = dao.insert(transaction(isSynced = true, syncAction = "NONE"))
        val readByTheEditScreen = dao.getById(id)!!
        dao.setSyncIdIfUnchanged(id, readByTheEditScreen.localVersion, "sheet-id")
        dao.setBaseIfUnchanged(id, readByTheEditScreen.localVersion, FINGERPRINT)

        dao.updateKeepingSyncState(readByTheEditScreen.copy(amount = 80.0, isSynced = false, syncAction = "UPDATE"))

        val saved = dao.getById(id)!!
        assertEquals(80.0, saved.amount, 0.0)
        assertEquals("sheet-id", saved.syncId)
        assertEquals(FINGERPRINT, saved.syncedRevision)
    }

    @Test
    fun `an edit of a Transaction that never synced keeps it an insert`() = runBlocking {
        val id = dao.insert(transaction())

        dao.updateKeepingSyncState(dao.getById(id)!!.copy(amount = 80.0, syncAction = "UPDATE"))

        assertEquals("INSERT", dao.getById(id)!!.syncAction)
    }

    @Test
    fun `a Transaction created on the phone gets an ID, while one inserted as already synced waits to be linked`() = runBlocking {
        val created = dao.insert(transaction())
        val imported = dao.insert(transaction(isSynced = true, syncAction = "NONE"))

        assertTrue(dao.getById(created)!!.syncId!!.isNotBlank())
        assertNull(dao.getById(imported)!!.syncId)
        assertEquals(1, dao.countWithoutSyncId())
    }

    @Test
    fun `sync bookkeeping never raises the version, so one Pull can apply several steps to a row`() = runBlocking {
        val id = dao.insert(transaction(isSynced = true, syncAction = "NONE"))
        val version = dao.getById(id)!!.localVersion

        assertEquals(1, dao.setSyncIdIfUnchanged(id, version, "sheet-id"))
        assertEquals(1, dao.setBaseIfUnchanged(id, version, FINGERPRINT))
        assertEquals(1, dao.setConflictIfUnchanged(id, version, "{}"))
        assertEquals(version, dao.getById(id)!!.localVersion)
        assertEquals(0, dao.setSyncIdIfUnchanged(id, version, "another-id"))
    }

    @Test
    fun `inserting a Sheet row twice keeps one copy`() = runBlocking {
        val sheetRow = transaction(isSynced = true, syncAction = "NONE").copy(syncId = "sheet-id")

        dao.insertIgnoringDuplicateSyncId(sheetRow)
        dao.insertIgnoringDuplicateSyncId(sheetRow)

        assertEquals(1, dao.getAllRecordsSnapshot().size)
    }

    @Test
    fun `bookmarking and bulk edits mark the change for Sync but keep a pending insert an insert`() = runBlocking {
        val pendingInsert = dao.insert(transaction())
        val synced = dao.insert(transaction(isSynced = true, syncAction = "NONE"))

        dao.updateBookmarkStatus(pendingInsert, true)
        dao.updateBookmarkStatus(synced, true)
        dao.updateTransactionsCategoryByIds(listOf(pendingInsert), "Travel")

        assertEquals("INSERT", dao.getById(pendingInsert)!!.syncAction)
        assertEquals("UPDATE", dao.getById(synced)!!.syncAction)
        assertFalse(dao.getById(synced)!!.isSynced)
    }

    @Test
    fun `deleting an account's Transactions marks them for a synced delete instead of erasing them`() = runBlocking {
        val accountId = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "Savings", initialBalance = 0.0))
        val id = dao.insert(transaction(accountId = accountId, isSynced = true, syncAction = "NONE"))

        dao.markLinkedTransactionsDeletedForAccount(accountId)

        assertEquals("DELETE", dao.getById(id)!!.syncAction)
        assertFalse(dao.getById(id)!!.isSynced)
        assertTrue(dao.getUnsyncedRecords().any { it.id == id })
    }

    private companion object {
        const val FINGERPRINT = "agreed-content"
    }

    private fun transaction(
        accountId: Long? = null,
        isSynced: Boolean = false,
        syncAction: String = "INSERT"
    ) = ExpenseRecord(
        date = "2026-09-24",
        type = "Expense",
        category = "Food",
        description = "Lunch",
        amount = 50.0,
        accountId = accountId,
        remarks = "",
        isSynced = isSynced,
        syncAction = syncAction
    )
}
