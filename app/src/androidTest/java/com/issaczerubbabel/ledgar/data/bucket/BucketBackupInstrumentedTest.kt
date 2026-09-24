package com.issaczerubbabel.ledgar.data.bucket

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.remote.BucketImportDto
import com.issaczerubbabel.ledgar.data.remote.CycleImportDto
import com.issaczerubbabel.ledgar.data.remote.CycleSyncDto
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room and SQLite on a device, against an in-memory database: the snapshot a backup reads and
 * the transaction a restore writes, including the constraints a restore must never trip.
 */
@RunWith(AndroidJUnit4::class)
class BucketBackupInstrumentedTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repo: BucketBudgetRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BucketBudgetRepositoryImpl(db, db.bucketBudgetDao())
    }

    @After
    fun tearDown() = db.close()

    private fun seed() = runBlocking {
        val old = repo.insertCycle(BudgetCycle(startDate = "2026-08-26", endDate = "2026-09-24", spendableAmount = 68000.0, closedAt = "2026-09-25"))
        val essentials = repo.insertBucket(BudgetBucket(cycleId = old, name = "Essentials", note = "Rent", colorIndex = 4, emoji = "🏠", allocatedAmount = 18000.0, sortOrder = 0))
        val fun_ = repo.insertBucket(BudgetBucket(cycleId = old, name = "Fun", colorIndex = 1, allocatedAmount = 6000.0, sortOrder = 1))
        repo.assignCategory(old, essentials, "Rent")
        repo.assignCategory(old, essentials, "Utilities")
        repo.assignCategory(old, fun_, "Food")
        repo.insertCycle(BudgetCycle(startDate = "2026-09-25", endDate = "2026-10-24", spendableAmount = 70000.0))
    }

    /** Ids differ after a restore, so compare the shape: everything except the database ids. */
    private fun shape(dtos: List<CycleSyncDto>) = dtos.map { c ->
        listOf(c.startDate, c.endDate, c.spendableAmount, c.closedAt) to
            c.buckets.map { listOf(it.name, it.note, it.colorIndex, it.emoji, it.allocatedAmount, it.sortOrder, it.categories) }
    }

    private fun CycleSyncDto.asImport() = CycleImportDto(
        id = id, startDate = startDate, endDate = endDate, spendableAmount = spendableAmount, closedAt = closedAt,
        buckets = buckets.map {
            BucketImportDto(it.id, it.name, it.note, it.colorIndex, it.emoji, it.allocatedAmount, it.sortOrder, it.categories)
        }
    )

    @Test
    fun aSnapshotBackedUpAndRestoredComesBackIdenticalInShape() = runBlocking {
        seed()
        val before = BucketBackupMapper.toSyncDtos(repo.getBackupSnapshot())

        repo.replaceAllFromBackup(BucketBackupMapper.fromImportDtos(before.map { it.asImport() }))

        assertEquals(shape(before), shape(BucketBackupMapper.toSyncDtos(repo.getBackupSnapshot())))
        assertEquals(2, repo.getBackupSnapshot().cycles.size)
    }

    @Test
    fun restoringReplacesWhateverWasThereInsteadOfAddingToIt() = runBlocking {
        seed()

        val onlyOne = CycleImportDto(startDate = "2026-01-01", endDate = "2026-01-31", spendableAmount = 10.0,
            buckets = listOf(BucketImportDto(name = "Solo", categories = listOf("Tea"))))
        repo.replaceAllFromBackup(BucketBackupMapper.fromImportDtos(listOf(onlyOne)))

        val snapshot = repo.getBackupSnapshot()
        assertEquals(1, snapshot.cycles.size)
        assertEquals(listOf("Solo"), snapshot.buckets.map { it.name })
        assertEquals(listOf("Tea"), snapshot.assignments.map { it.category })
    }

    @Test
    fun aMessyRestoreNeverTripsADatabaseConstraint() = runBlocking {
        // Duplicate categories that differ by case, across buckets and within one, and several
        // cycles claiming to be running. Uncleaned, the unique index would reject this.
        val messy = listOf(
            CycleImportDto(startDate = "2026-08-01", endDate = "2026-08-31", spendableAmount = 1.0,
                buckets = listOf(
                    BucketImportDto(name = "A", categories = listOf("Food", "food", " FOOD ")),
                    BucketImportDto(name = "B", categories = listOf("FOOD", "Rent"))
                )),
            CycleImportDto(startDate = "2026-09-01", endDate = "2026-09-30", spendableAmount = 1.0,
                buckets = listOf(BucketImportDto(name = "C", categories = listOf("Food"))))
        )

        repo.replaceAllFromBackup(BucketBackupMapper.fromImportDtos(messy))

        val snapshot = repo.getBackupSnapshot()
        assertEquals(1, snapshot.assignments.count { it.cycleId == snapshot.cycles[0].id && it.category.equals("food", ignoreCase = true) })
        assertEquals(1, snapshot.cycles.count { it.closedAt == null }) // only the newest is running
        assertEquals("2026-08-31", snapshot.cycles[0].closedAt)
    }

    @Test
    fun theRestoredRunningCycleIsTheOneTheAppTreatsAsCurrent() = runBlocking {
        seed()
        repo.replaceAllFromBackup(BucketBackupMapper.fromImportDtos(BucketBackupMapper.toSyncDtos(repo.getBackupSnapshot()).map { it.asImport() }))

        assertEquals("2026-09-25", repo.getRunningCycle()!!.startDate)
    }

    @Test
    fun anEmptyDatabaseGivesAnEmptySnapshot() = runBlocking {
        val snapshot = repo.getBackupSnapshot()

        assertTrue(snapshot.cycles.isEmpty() && snapshot.buckets.isEmpty() && snapshot.assignments.isEmpty())
        assertNull(repo.getRunningCycle())
    }
}
