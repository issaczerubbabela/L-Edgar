package com.issaczerubbabel.ledgar.data.bucket

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Runs the real Room + SQLite stack on a device against an in-memory database, so it never
 * touches the app's own data. Covers what the pure unit tests cannot: that the transaction in
 * startCycle really closes, opens and copies together, with foreign keys enforced.
 */
@RunWith(AndroidJUnit4::class)
class StartCycleInstrumentedTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repo: BucketBudgetRepositoryImpl

    private val today = LocalDate.of(2026, 9, 26)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BucketBudgetRepositoryImpl(db, db.bucketBudgetDao())
    }

    @After
    fun tearDown() = db.close()

    private fun request(
        start: LocalDate = today,
        end: LocalDate = LocalDate.of(2026, 10, 25),
        amount: Double = 70000.0,
        carryOver: Boolean = true
    ) = StartCycleRequest(start, end, amount, carryOver)

    /** A running cycle with two buckets and three routed categories. */
    private fun seedRunningCycle(): Long = runBlocking {
        val cycleId = repo.insertCycle(
            BudgetCycle(startDate = "2026-08-26", endDate = "2026-09-25", spendableAmount = 68000.0)
        )
        val essentials = repo.insertBucket(
            BudgetBucket(cycleId = cycleId, name = "Essentials", note = "Rent and bills", colorIndex = 4,
                emoji = "🏠", allocatedAmount = 18000.0, sortOrder = 0)
        )
        val eatingOut = repo.insertBucket(
            BudgetBucket(cycleId = cycleId, name = "Eating Out", colorIndex = 1, allocatedAmount = 6000.0, sortOrder = 1)
        )
        repo.assignCategory(cycleId, essentials, "Rent")
        repo.assignCategory(cycleId, essentials, "Utilities")
        repo.assignCategory(cycleId, eatingOut, "Restaurants")
        cycleId
    }

    @Test
    fun theVeryFirstCycleOpensCleanly() = runBlocking {
        val result = repo.startCycle(request(), today)

        val started = result as StartCycleResult.Started
        val running = repo.getRunningCycle()
        assertEquals(started.cycleId, running!!.id)
        assertEquals("2026-09-26", running.startDate)
        assertEquals(70000.0, running.spendableAmount, 0.0)
        assertTrue(repo.getBuckets(started.cycleId).isEmpty())
    }

    @Test
    fun aHandoverClosesTheOldCycleAndOpensTheNewOne() = runBlocking {
        val oldId = seedRunningCycle()

        val started = repo.startCycle(request(), today) as StartCycleResult.Started

        val old = repo.getCycle(oldId)!!
        assertEquals("2026-09-25", old.endDate)
        assertEquals("2026-09-26", old.closedAt)
        assertEquals(started.cycleId, repo.getRunningCycle()!!.id)
        assertNotEquals(oldId, started.cycleId)
    }

    @Test
    fun exactlyOneCycleIsRunningAfterAHandover() = runBlocking {
        seedRunningCycle()
        repo.startCycle(request(), today)

        val all = repo.observeAllCycles().first()
        assertEquals(2, all.size)
        assertEquals(1, all.count { it.closedAt == null })
    }

    @Test
    fun carryingOverCopiesBucketsAndRoutingIntoTheNewCycle() = runBlocking {
        val oldId = seedRunningCycle()

        val newId = (repo.startCycle(request(), today) as StartCycleResult.Started).cycleId

        val newBuckets = repo.getBuckets(newId)
        assertEquals(listOf("Essentials", "Eating Out"), newBuckets.map { it.name })
        assertEquals(listOf(18000.0, 6000.0), newBuckets.map { it.allocatedAmount })
        // Everything about a bucket comes across, not just its name and amount.
        val essentials = newBuckets.first { it.name == "Essentials" }
        assertEquals("Rent and bills", essentials.note)
        assertEquals(4, essentials.colorIndex)
        assertEquals("🏠", essentials.emoji)

        // Routing points at the NEW buckets, not the old ones.
        val routing = repo.getCategoryAssignments(newId).associate { it.category to it.bucketId }
        assertEquals(essentials.id, routing["Rent"])
        assertEquals(essentials.id, routing["Utilities"])
        assertEquals(newBuckets.first { it.name == "Eating Out" }.id, routing["Restaurants"])
        assertTrue(newBuckets.map { it.cycleId }.all { it == newId })
        assertTrue(routing.values.all { id -> newBuckets.any { it.id == id } })

        assertNotEquals(repo.getBuckets(oldId).map { it.id }.toSet(), newBuckets.map { it.id }.toSet())
    }

    @Test
    fun theClosedCycleKeepsItsBucketsAsHistory() = runBlocking {
        val oldId = seedRunningCycle()

        repo.startCycle(request(), today)

        assertEquals(2, repo.getBuckets(oldId).size)
        assertEquals(3, repo.getCategoryAssignments(oldId).size)
    }

    @Test
    fun notCarryingOverLeavesTheNewCycleEmpty() = runBlocking {
        seedRunningCycle()

        val newId = (repo.startCycle(request(carryOver = false), today) as StartCycleResult.Started).cycleId

        assertTrue(repo.getBuckets(newId).isEmpty())
        assertTrue(repo.getCategoryAssignments(newId).isEmpty())
    }

    @Test
    fun aBackdatedStartTrimsTheOldCycle() = runBlocking {
        val oldId = seedRunningCycle()

        repo.startCycle(request(start = LocalDate.of(2026, 9, 20)), today)

        assertEquals("2026-09-19", repo.getCycle(oldId)!!.endDate)
    }

    @Test
    fun anOverdueCycleIsStretchedOverTheGapWhenTheNextOneStartsLate() = runBlocking {
        val oldId = seedRunningCycle()

        repo.startCycle(request(start = LocalDate.of(2026, 9, 29)), LocalDate.of(2026, 9, 29))

        assertEquals("2026-09-28", repo.getCycle(oldId)!!.endDate)
    }

    @Test
    fun aRejectedRequestChangesNothing() = runBlocking {
        val oldId = seedRunningCycle()
        val before = repo.getCycle(oldId)

        // Starting on the day the running cycle began is not allowed.
        val result = repo.startCycle(request(start = LocalDate.of(2026, 8, 26)), today)

        assertEquals(StartCycleResult.Rejected(StartCycleError.START_NOT_AFTER_CURRENT), result)
        assertEquals(before, repo.getCycle(oldId))
        assertEquals(1, repo.observeAllCycles().first().size)
        assertNull(repo.getCycle(oldId)!!.closedAt)
    }

    @Test
    fun assigningACategoryMovesItInsteadOfDuplicatingIt() = runBlocking {
        val cycleId = seedRunningCycle()
        val eatingOut = repo.getBuckets(cycleId).first { it.name == "Eating Out" }

        // "rent" differs only by case from the "Rent" already routed into Essentials.
        repo.assignCategory(cycleId, eatingOut.id, "rent")

        val rentRows = repo.getCategoryAssignments(cycleId).filter { it.category.equals("rent", ignoreCase = true) }
        assertEquals(1, rentRows.size)
        assertEquals(eatingOut.id, rentRows.single().bucketId)
    }

    @Test
    fun settingABucketsCategoriesMakesThemExactlyThatSetAndReleasesTheRest() = runBlocking {
        val cycleId = seedRunningCycle()
        val essentials = repo.getBuckets(cycleId).first { it.name == "Essentials" }
        val eatingOut = repo.getBuckets(cycleId).first { it.name == "Eating Out" }

        // Essentials held Rent and Utilities. Now it should hold Utilities and Restaurants (which
        // lives in Eating Out): Rent is released, Restaurants moves across.
        repo.setBucketCategories(cycleId, essentials.id, setOf("Utilities", "restaurants"))

        val routing = repo.getCategoryAssignments(cycleId).associate { it.category.lowercase() to it.bucketId }
        assertEquals(essentials.id, routing["utilities"])
        assertEquals(essentials.id, routing["restaurants"])
        assertNull(routing["rent"])
        // Eating Out lost its only category to the move, and nothing was duplicated.
        assertTrue(repo.getCategoryAssignments(cycleId).none { it.bucketId == eatingOut.id })
        assertEquals(2, repo.getCategoryAssignments(cycleId).size)
    }

    @Test
    fun anEmptySetReleasesEverythingTheBucketHeld() = runBlocking {
        val cycleId = seedRunningCycle()
        val essentials = repo.getBuckets(cycleId).first { it.name == "Essentials" }

        repo.setBucketCategories(cycleId, essentials.id, emptySet())

        assertTrue(repo.getCategoryAssignments(cycleId).none { it.bucketId == essentials.id })
        assertEquals(1, repo.getCategoryAssignments(cycleId).size) // Restaurants, in Eating Out, is untouched
    }

    @Test
    fun deletingABucketReleasesItsCategoriesToUnbucketed() = runBlocking {
        val cycleId = seedRunningCycle()
        val essentials = repo.getBuckets(cycleId).first { it.name == "Essentials" }

        repo.deleteBucket(essentials)

        assertEquals(1, repo.getBuckets(cycleId).size)
        assertNull(repo.getCategoryAssignments(cycleId).firstOrNull { it.category == "Rent" })
        assertNull(repo.getCategoryAssignments(cycleId).firstOrNull { it.category == "Utilities" })
    }

    @Test
    fun unassigningPutsACategoryBackToUnbucketed() = runBlocking {
        val cycleId = seedRunningCycle()

        repo.unassignCategory(cycleId, "Rent")

        assertNull(repo.getCategoryAssignments(cycleId).firstOrNull { it.category == "Rent" })
        assertNotNull(repo.getCategoryAssignments(cycleId).firstOrNull { it.category == "Utilities" })
    }
}
