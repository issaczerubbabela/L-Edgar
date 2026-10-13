package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
class UndoableActionsTest {

    private lateinit var db: SheetSyncDatabase
    private val writes = mutableListOf<String>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.holder() = UndoableActions(db.expenseDao(), backgroundScope)

    private fun action(name: String, transactions: Set<Long> = emptySet(), captures: Set<Long> = emptySet()) =
        UndoableAction(name, hiddenTransactionIds = transactions, hiddenCaptureIds = captures) { writes += name }

    @Test
    fun nothingIsWrittenBeforeTheWindowEnds() = runTest {
        val holder = holder()

        holder.perform(action("Confirmed Instamart", captures = setOf(7)))
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS - 1)

        assertTrue(writes.isEmpty())
        assertEquals(setOf(7L), holder.hiddenCaptureIds.value)
        assertEquals("Confirmed Instamart", holder.pending.value?.message)
    }

    @Test
    fun undoWithinTheWindowWritesNothingAndShowsTheRowsAgain() = runTest {
        val holder = holder()
        val confirm = action("Confirmed Instamart", captures = setOf(7))

        holder.perform(confirm)
        advanceTimeBy(1_000)
        holder.undo(confirm)
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS * 2)

        assertTrue(writes.isEmpty())
        assertTrue(holder.hiddenCaptureIds.value.isEmpty())
        assertNull(holder.pending.value)
    }

    @Test
    fun afterTheWindowTheActionIsWrittenExactlyOnce() = runTest {
        val holder = holder()

        holder.perform(action("Dismissed Instamart", captures = setOf(7)))
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS + 1)
        holder.commit()
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS * 2)

        assertEquals(listOf("Dismissed Instamart"), writes)
        assertNull(holder.pending.value)
        assertTrue(holder.hiddenCaptureIds.value.isEmpty())
    }

    @Test
    fun aSecondActionWritesTheFirstStraightAway() = runTest {
        val holder = holder()
        val first = action("first", captures = setOf(1))

        holder.perform(first)
        holder.perform(action("second", captures = setOf(2)))
        runCurrent()

        assertEquals(listOf("first"), writes)
        assertEquals("second", holder.pending.value?.message)
        // Undo can't bring back a decision that's already final; its rows stay hidden until Room catches up.
        holder.undo(first)
        assertEquals(setOf(1L, 2L), holder.hiddenCaptureIds.value)
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS - 1)
        assertEquals(setOf(2L), holder.hiddenCaptureIds.value)
    }

    @Test
    fun aBatchIsUndoneAsAWhole() = runTest {
        val holder = holder()
        val batch = action("Confirmed 3 · ₹2,140", captures = setOf(1, 2, 3))

        holder.perform(batch)
        assertEquals(setOf(1L, 2L, 3L), holder.hiddenCaptureIds.value)
        holder.undo(batch)
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS * 2)

        assertTrue(writes.isEmpty())
        assertTrue(holder.hiddenCaptureIds.value.isEmpty())
    }

    @Test
    fun aDeleteHidesTheTransactionAndSoftDeletesItOnceFinal() = runTest {
        val holder = holder()
        val id = db.expenseDao().insert(
            ExpenseRecord(
                date = "2026-10-13", type = "Expense", category = "Food", description = "Lunch", amount = 120.0,
                remarks = "", isSynced = true, syncAction = "NONE"
            )
        )
        val lunch = db.expenseDao().getById(id)!!

        holder.delete(lunch, "Deleted “Lunch” · ₹120")
        assertEquals(setOf(id), holder.hiddenTransactionIds.value)
        assertEquals("NONE", db.expenseDao().getById(id)!!.syncAction)

        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS + 1)

        assertEquals("DELETE", db.expenseDao().getById(id)!!.syncAction)
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS)
        assertTrue(holder.hiddenTransactionIds.value.isEmpty())
    }
}
