package com.issaczerubbabel.ledgar.capture

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.notify.CaptureNotifier
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
import com.issaczerubbabel.ledgar.data.repository.CaptureRepositoryImpl
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.viewmodel.CaptureInbox
import com.issaczerubbabel.ledgar.viewmodel.CaptureRowUi
import com.issaczerubbabel.ledgar.viewmodel.CaptureRows
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Confirm day's sheet (#142): only the ticked rows are written, and one Undo brings them all back. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureBulkConfirmTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repo: CaptureRepositoryImpl
    private var hdfc = 0L
    private var hash = 0

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repo = CaptureRepositoryImpl(
            db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao(), {}
        )
        hdfc = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun capture(merchant: String, confidence: Double, category: String? = "Food"): CapturedTransaction {
        val id = db.captureDao().insert(
            CapturedTransaction(
                sources = "NOTIF", sender = "HDFC Bank", rawText = "Sent Rs.100 to $merchant", rawHash = "hash-${hash++}",
                capturedAt = T0, txnTime = T0, amount = 100.0, direction = "DEBIT", channel = "UPI",
                merchantRaw = merchant, merchantNorm = merchant, accountHint = "1234", accountId = hdfc,
                suggestedType = TransactionType.EXPENSE, suggestedCategory = category, confidence = confidence
            )
        )
        return db.captureDao().getById(id)!!
    }

    /** The waiting cards, as To review builds them. */
    private suspend fun pending(): List<CaptureRowUi> =
        listOf(high, check, low).map { CaptureRows.build(db.captureDao().getById(it.id)!!, null, emptyMap(), setOf(hdfc)) }

    private lateinit var high: CapturedTransaction
    private lateinit var check: CapturedTransaction
    private lateinit var low: CapturedTransaction

    private fun TestScope.confirmer(actions: UndoableActions) = CaptureConfirmer(
        repo, actions,
        CaptureNotifier(ApplicationProvider.getApplicationContext(), db.captureDao(), db.accountDao(), repo),
        ReminderPreferences(ApplicationProvider.getApplicationContext())
    )

    private suspend fun status(c: CapturedTransaction) = db.captureDao().getById(c.id)!!.status

    private suspend fun seed() {
        high = capture("SWIGGY", confidence = 0.98)
        check = capture("ZOMATO", confidence = 0.75)
        low = capture("SOMEONE", confidence = 0.3, category = null)
    }

    @Test
    fun confirmingTheSheetAsItOpensWritesOnlyTheHighRow() = runTest {
        seed()
        val actions = UndoableActions(db.expenseDao(), backgroundScope)
        val sheet = CaptureInbox.bulkSheet(pending())
        assertEquals("Low is left out", listOf(high.id, check.id), sheet.map { it.row.capture.id })

        confirmer(actions).confirm(CaptureInbox.confirmations(CaptureInbox.ticked(sheet, emptySet())), emptyMap())
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS + 1)
        advanceUntilIdle()

        assertEquals("CONFIRMED", status(high))
        assertEquals("an unticked Check row is never written", "PENDING", status(check))
        assertEquals("PENDING", status(low))
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
    }

    @Test
    fun oneUndoBringsBackEveryTickedRowAndWritesNothing() = runTest {
        seed()
        val actions = UndoableActions(db.expenseDao(), backgroundScope)
        val sheet = CaptureInbox.bulkSheet(pending())
        // The user also ticks the Check row.
        val ticked = CaptureInbox.ticked(sheet, setOf(check.id))

        confirmer(actions).confirm(CaptureInbox.confirmations(ticked), emptyMap())
        val action = actions.pending.value!!
        assertEquals("Confirmed 2 · ₹200", action.message)
        assertEquals(setOf(high.id, check.id), actions.hiddenCaptureIds.value)

        actions.undo(action)
        advanceUntilIdle()

        assertEquals(listOf("PENDING", "PENDING"), listOf(status(high), status(check)))
        assertTrue(actions.hiddenCaptureIds.value.isEmpty())
        assertTrue(db.expenseDao().getAllRecordsSnapshot().isEmpty())
    }

    private companion object {
        const val T0 = 1_794_657_600_000L
    }
}
