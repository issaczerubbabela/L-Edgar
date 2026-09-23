package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseVersionTrigger
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
                override fun onCreate(db: SupportSQLiteDatabase) = ExpenseVersionTrigger.install(db)
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
        assertEquals(0, dao.markSyncedIfUnchanged(id, sentVersion))
        assertFalse(dao.getById(id)!!.isSynced)

        assertEquals(1, dao.markSyncedIfUnchanged(id, dao.getById(id)!!.localVersion))
        assertTrue(dao.getById(id)!!.isSynced)
        assertEquals("NONE", dao.getById(id)!!.syncAction)
    }

    @Test
    fun `settling never undoes a pending delete`() = runBlocking {
        val id = dao.insert(transaction())
        dao.markTransactionDeletedById(id)

        assertEquals(0, dao.markSyncedIfUnchanged(id, dao.getById(id)!!.localVersion))
        assertEquals("DELETE", dao.getById(id)!!.syncAction)
    }

    @Test
    fun `a synced delete removes the row only if it is still the same delete`() = runBlocking {
        val id = dao.insert(transaction())
        dao.markTransactionDeletedById(id)
        val version = dao.getById(id)!!.localVersion

        assertEquals(0, dao.deleteSyncedDeleteIfUnchanged(id, version - 1))
        assertNotNull(dao.getById(id))
        assertEquals(1, dao.deleteSyncedDeleteIfUnchanged(id, version))
        assertNull(dao.getById(id))
    }

    @Test
    fun `a Remote timestamp is assigned once and never overwritten`() = runBlocking {
        val id = dao.insert(transaction())

        assertEquals(1, dao.assignRemoteTimestamp(id, "9/24/2026 10:00:00"))
        assertEquals(0, dao.assignRemoteTimestamp(id, "9/24/2026 10:00:01"))
        assertEquals("9/24/2026 10:00:00", dao.getById(id)!!.remoteTimestamp)
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
