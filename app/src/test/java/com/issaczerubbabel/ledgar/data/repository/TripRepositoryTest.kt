package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.trip.SplitMode
import com.issaczerubbabel.ledgar.trip.TripExpenseInput
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TripRepositoryTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repo: TripRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TripRepositoryImpl(db, db.tripDao(), db.expenseDao(), db.captureDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun goaTrip(): TripDetail {
        val id = repo.createTrip("Goa trip", "2026-10-10", "2026-10-12", listOf("Rahul", " ", "Priya"))
        return repo.observeDetail(id).first()!!
    }

    private fun TripDetail.member(name: String) = members.first { it.name == name }.id

    private fun TripDetail.expense(amount: Long, payer: String, purpose: String, category: String = "Food & Snacks", among: List<String>? = null) =
        TripExpenseInput(
            id = 0,
            date = "2026-10-10",
            purpose = purpose,
            amountPaise = amount,
            payerId = member(payer),
            mode = SplitMode.EQUAL,
            memberIds = (among ?: members.map { it.name }).map { member(it) },
            category = category
        )

    @Test
    fun aNewTripStartsWithYouAndTheNamedMembers() = runBlocking {
        val trip = goaTrip()
        assertEquals(listOf("You", "Rahul", "Priya"), trip.members.map { it.name })
        assertTrue(trip.members.first().isSelf)
        assertFalse(trip.trip.isArchived)
        assertEquals(trip.trip.id, repo.observeActiveTrip().first()?.id)
    }

    @Test
    fun savingAnExpenseStoresEachShare() = runBlocking {
        val trip = goaTrip()
        repo.saveExpense(trip.trip.id, trip.expense(30000, "You", "Dinner"))
        val saved = repo.observeDetail(trip.trip.id).first()!!.expenses.single()
        assertEquals(30000, saved.amountPaise)
        assertEquals(listOf(trip.member("You"), trip.member("Rahul"), trip.member("Priya")), saved.memberIds)
        assertEquals(listOf(10000L, 10000L, 10000L), db.tripDao().getSharesFor(saved.id).map { it.sharePaise })
    }

    @Test
    fun aMemberOnAnExpenseCannotBeRemoved() = runBlocking {
        val trip = goaTrip()
        repo.saveExpense(trip.trip.id, trip.expense(20000, "Rahul", "Cab", among = listOf("You", "Rahul")))
        assertFalse(repo.removeMember(trip.member("Rahul")))
        assertTrue(repo.removeMember(trip.member("Priya")))
        assertEquals(listOf("You", "Rahul"), repo.observeDetail(trip.trip.id).first()!!.members.map { it.name })
    }

    @Test
    fun postWritesOnlyTheReviewedSharesAndArchivesTheTrip() = runBlocking {
        val trip = goaTrip()
        val dinner = repo.saveExpense(trip.trip.id, trip.expense(30000, "You", "Dinner"))
        repo.saveExpense(trip.trip.id, trip.expense(9000, "Rahul", "Parasailing", among = listOf("Rahul", "Priya")))

        val posted = repo.post(
            trip.trip.id,
            listOf(PostRow(dinner, "2026-10-10", "Goa trip · Dinner", "Food & Snacks", 10000, accountId = null))
        )

        assertEquals(1, posted)
        val record = db.expenseDao().getAllRecordsSnapshot().single()
        assertEquals("Goa trip · Dinner", record.description)
        assertEquals(100.0, record.amount, 0.0)
        assertEquals("Expense", record.type)
        assertEquals("INSERT", record.syncAction)
        assertFalse(record.isSynced)
        assertEquals(record.id, db.tripDao().getExpense(dinner)!!.postedExpenseId)
        val after = repo.observeDetail(trip.trip.id).first()!!
        assertTrue(after.trip.isArchived)
        assertEquals(1, after.postedCount)
        assertNull(repo.observeActiveTrip().first())
    }

    @Test
    fun anArchivedTripIsReadOnly() = runBlocking {
        val trip = goaTrip()
        val id = repo.saveExpense(trip.trip.id, trip.expense(30000, "You", "Dinner"))
        repo.post(trip.trip.id, listOf(PostRow(id, "2026-10-10", "Goa trip · Dinner", "Food & Snacks", 10000, null)))
        assertTrue(runCatching { repo.saveExpense(trip.trip.id, trip.expense(100, "You", "Late")) }.exceptionOrNull() is TripArchivedException)
        assertTrue(runCatching { repo.addMember(trip.trip.id, "Arjun") }.exceptionOrNull() is TripArchivedException)
        assertTrue(runCatching { repo.recordSettlement(trip.trip.id, trip.member("Rahul"), trip.member("You"), 100, "2026-10-12") }.exceptionOrNull() is TripArchivedException)
    }

    @Test
    fun unarchiveDeletesThePostedTransactionsThroughTheNormalDelete() = runBlocking {
        val trip = goaTrip()
        val id = repo.saveExpense(trip.trip.id, trip.expense(30000, "You", "Dinner"))
        repo.post(trip.trip.id, listOf(PostRow(id, "2026-10-10", "Goa trip · Dinner", "Food & Snacks", 10000, null)))

        assertEquals(1, repo.unarchive(trip.trip.id))

        val record = db.expenseDao().getAllRecordsSnapshot().single()
        assertEquals("DELETE", record.syncAction)
        assertFalse(record.isSynced)
        assertNull(db.tripDao().getExpense(id)!!.postedExpenseId)
        assertFalse(repo.observeDetail(trip.trip.id).first()!!.trip.isArchived)
    }

    @Test
    fun addToTripMakesATripExpenseAndNoTransaction() = runBlocking {
        val trip = goaTrip()
        val captureId = db.captureDao().insert(
            CapturedTransaction(
                sources = "NOTIF", sender = "HDFC Bank", rawText = "Sent Rs.1150", rawHash = "h1",
                capturedAt = 1, txnTime = 1, amount = 1150.0, direction = "DEBIT", channel = "UPI"
            )
        )

        repo.addCaptureToTrip(captureId, trip.trip.id, trip.expense(115000, "You", "Zantye's Cafe"))

        assertEquals(1, repo.observeDetail(trip.trip.id).first()!!.expenses.size)
        assertTrue(db.expenseDao().getAllRecordsSnapshot().isEmpty())
        assertTrue(db.captureDao().observePending().first().isEmpty())
        assertEquals("TRIP", db.captureDao().getById(captureId)!!.status)
    }
}
