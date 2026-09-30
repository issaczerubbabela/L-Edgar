package com.issaczerubbabel.ledgar.sync

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseTableTriggers
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import kotlinx.coroutines.flow.first
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

/** The real Room store that the syncer tests' fake mirrors. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class RoomTransactionSyncStoreTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var store: RoomTransactionSyncStore
    private var walletId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = ExpenseTableTriggers.install(db)
            })
            .allowMainThreadQueries()
            .build()
        store = RoomTransactionSyncStore(db, SheetTransactionMapper(db.accountDao()))
        walletId = db.accountDao().insert(AccountRecord(groupName = "Cash", accountName = "Wallet", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a Sheet row comes in synced, under its ID, on the Account it names`() = runBlocking {
        val row = PulledRow("sheet-1", sheetRow(amount = 30.0).copy(revision = "rev-1"))

        assertEquals(1, store.apply(listOf(MergeStep.InsertFromSheet(row))))

        val local = db.expenseDao().getAllRecordsSnapshot().single()
        assertEquals("sheet-1", local.syncId)
        assertEquals(walletId, local.accountId)
        assertTrue(local.isSynced)
        assertEquals("rev-1", local.syncedRevision)
        assertEquals("the Sheet's copy and the phone's now fingerprint alike",
            row.content.fingerprint, TransactionContent.of(local, store.accountNamesById()).fingerprint)
    }

    @Test
    fun `the same Sheet row inserted twice stays one Transaction`() = runBlocking {
        val row = PulledRow("sheet-1", sheetRow())

        store.apply(listOf(MergeStep.InsertFromSheet(row)))
        store.apply(listOf(MergeStep.InsertFromSheet(row)))

        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
    }

    @Test
    fun `linking and taking the Sheet's version apply to one row in the same Pull`() = runBlocking {
        val id = db.expenseDao().insert(legacyRow(amount = 50.0))
        val version = db.expenseDao().getById(id)!!.localVersion
        val row = PulledRow("sheet-1", sheetRow(amount = 75.0))

        val applied = store.apply(listOf(
            MergeStep.Link(id, version, "sheet-1"),
            MergeStep.ApplyFromSheet(id, version, row)
        ))

        val local = db.expenseDao().getById(id)!!
        assertEquals(2, applied)
        assertEquals("sheet-1", local.syncId)
        assertEquals(75.0, local.amount, 0.0)
        assertTrue(local.isSynced)
    }

    @Test
    fun `a step planned from an older version of the row is skipped`() = runBlocking {
        val id = db.expenseDao().insert(legacyRow(amount = 50.0))
        val plannedVersion = db.expenseDao().getById(id)!!.localVersion
        db.expenseDao().updateTransactionsDescriptionByIds(listOf(id), "edited on the phone meanwhile")

        val applied = store.apply(listOf(MergeStep.ApplyFromSheet(id, plannedVersion, PulledRow("sheet-1", sheetRow(amount = 99.0)))))

        assertEquals(0, applied)
        assertEquals(50.0, db.expenseDao().getById(id)!!.amount, 0.0)
    }

    @Test
    fun `a conflict keeps the Sheet's version for the user to compare`() = runBlocking {
        val id = db.expenseDao().insert(legacyRow(amount = 50.0))
        val dto = sheetRow(amount = 70.0).copy(id = "sheet-1")

        store.apply(listOf(MergeStep.Conflict(id, db.expenseDao().getById(id)!!.localVersion, PulledRow("sheet-1", dto))))

        val stored = db.expenseDao().getById(id)!!.sheetConflictJson
        assertEquals(dto, Gson().fromJson(stored, ImportRecordDto::class.java))
        assertEquals(1, db.expenseDao().observeConflicts().first().size)
    }

    @Test
    fun `a delete only removes the row the plan saw`() = runBlocking {
        val id = db.expenseDao().insert(legacyRow())
        val version = db.expenseDao().getById(id)!!.localVersion

        store.apply(listOf(MergeStep.DeleteLocal(id, version + 1)))
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)

        store.apply(listOf(MergeStep.DeleteLocal(id, version)))
        assertNull(db.expenseDao().getById(id))
    }

    private fun sheetRow(amount: Double = 20.0) = ImportRecordDto(
        timestamp = "9/24/2026 08:00:00",
        date = "2026-09-24",
        type = "Expense",
        expCategory = "Food",
        description = "Lunch",
        amount = amount,
        accountName = "Wallet",
        remarks = ""
    )

    private fun legacyRow(amount: Double = 20.0) = ExpenseRecord(
        date = "2026-09-24",
        type = "Expense",
        category = "Food",
        description = "Lunch",
        amount = amount,
        accountId = walletId,
        remarks = "",
        isSynced = true,
        syncAction = "NONE",
        remoteTimestamp = "9/24/2026 08:00:00"
    )
}
