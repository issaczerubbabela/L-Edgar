package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration
import com.issaczerubbabel.ledgar.data.remote.BucketImportDto
import com.issaczerubbabel.ledgar.data.remote.CycleImportDto
import com.issaczerubbabel.ledgar.data.remote.CycleSyncDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BucketBackupMapperTest {

    // ── toSyncDtos ─────────────────────────────────────────────────────────────

    private val snapshot = BucketBudgetSnapshot(
        cycles = listOf(
            BudgetCycle(id = 2, startDate = "2026-09-25", endDate = "2026-10-24", spendableAmount = 70000.0),
            BudgetCycle(id = 1, startDate = "2026-08-26", endDate = "2026-09-24", spendableAmount = 68000.0, closedAt = "2026-09-25")
        ),
        buckets = listOf(
            BudgetBucket(id = 11, cycleId = 1, name = "Eating Out", colorIndex = 1, allocatedAmount = 6000.0, sortOrder = 1),
            BudgetBucket(id = 10, cycleId = 1, name = "Essentials", note = "Rent", colorIndex = 4, emoji = "🏠",
                allocatedAmount = 18000.0, sortOrder = 0)
        ),
        assignments = listOf(
            BucketCategory(id = 1, cycleId = 1, bucketId = 10, category = "Utilities"),
            BucketCategory(id = 2, cycleId = 1, bucketId = 10, category = "Rent"),
            BucketCategory(id = 3, cycleId = 1, bucketId = 11, category = "Food")
        )
    )

    @Test
    fun aBackupNestsBucketsAndCategoriesInsideTheirCycleOldestFirst() {
        val dtos = BucketBackupMapper.toSyncDtos(snapshot)

        assertEquals(listOf(1L, 2L), dtos.map { it.id })
        val first = dtos[0]
        assertEquals("2026-09-25", first.closedAt)
        assertEquals(listOf("Essentials", "Eating Out"), first.buckets.map { it.name }) // by sortOrder
        assertEquals(listOf("Rent", "Utilities"), first.buckets[0].categories)          // alphabetical
        assertEquals("🏠", first.buckets[0].emoji)
        assertTrue(dtos[1].buckets.isEmpty())
        assertNull(dtos[1].closedAt)
    }

    @Test
    fun anEmptyDatabaseBacksUpNothing() {
        assertTrue(BucketBackupMapper.toSyncDtos(BucketBudgetSnapshot(emptyList(), emptyList(), emptyList())).isEmpty())
    }

    @Test
    fun whatIsBackedUpComesBackUnchanged() {
        // The full round trip the sheets perform: database -> DTOs -> (sheet) -> import DTOs -> restore.
        val restored = BucketBackupMapper.fromImportDtos(BucketBackupMapper.toSyncDtos(snapshot).map { it.asImport() })

        assertEquals(2, restored.size)
        val old = restored[0]
        assertEquals("2026-08-26", old.startDate)
        assertEquals(68000.0, old.spendableAmount, 0.0)
        assertEquals("2026-09-25", old.closedAt)
        assertEquals(listOf("Essentials", "Eating Out"), old.buckets.map { it.name })
        assertEquals(listOf("Rent", "Utilities"), old.buckets[0].categories)
        assertEquals(4, old.buckets[0].colorIndex)
        assertEquals("Rent", old.buckets[0].note)
        assertNull(restored[1].closedAt)
    }

    // ── fromImportDtos: rejecting a hand-edited sheet ─────────────────────────

    private fun cycle(
        start: String? = "2026-09-01",
        end: String? = "2026-09-30",
        amount: Double? = 1000.0,
        closedAt: String? = null,
        buckets: List<BucketImportDto>? = emptyList()
    ) = CycleImportDto(startDate = start, endDate = end, spendableAmount = amount, closedAt = closedAt, buckets = buckets)

    private fun bucket(name: String? = "Food", vararg categories: String, allocated: Double? = 100.0, color: Int? = 0) =
        BucketImportDto(name = name, allocatedAmount = allocated, colorIndex = color, categories = categories.toList())

    @Test
    fun aCycleWithoutReadableDatesIsDropped() {
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(cycle(start = null), cycle(end = ""), cycle(start = "yesterday"), cycle(end = "2026-13-40"), cycle())
        )

        assertEquals(1, restored.size)
    }

    @Test
    fun aCycleThatEndsBeforeItStartsIsDropped() {
        assertTrue(BucketBackupMapper.fromImportDtos(listOf(cycle(start = "2026-09-30", end = "2026-09-01"))).isEmpty())
    }

    @Test
    fun aBucketNeedsANameAndKeepsItsPositionOtherwise() {
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(cycle(buckets = listOf(bucket(name = null), bucket(name = "   "), bucket(name = "  Fun  "))))
        )

        assertEquals(listOf("Fun"), restored.single().buckets.map { it.name })
    }

    @Test
    fun aCategoryLivesInOneBucketPerCycleEvenIfTheSheetSaysOtherwise() {
        // The database would reject the second one at its unique index and fail the whole restore.
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(
                cycle(
                    buckets = listOf(
                        bucket("A", "Food", "Rent"),
                        bucket("B", "food", "RENT", "Travel")
                    )
                )
            )
        )

        val buckets = restored.single().buckets
        assertEquals(listOf("Food", "Rent"), buckets[0].categories)
        assertEquals(listOf("Travel"), buckets[1].categories)
    }

    @Test
    fun theSameCategoryInDifferentCyclesIsFine() {
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(
                cycle(start = "2026-08-01", end = "2026-08-31", closedAt = "2026-08-31", buckets = listOf(bucket("A", "Food"))),
                cycle(buckets = listOf(bucket("A", "Food")))
            )
        )

        assertEquals(listOf("Food"), restored[0].buckets.single().categories)
        assertEquals(listOf("Food"), restored[1].buckets.single().categories)
    }

    @Test
    fun blankAndRepeatedCategoriesAreCleanedUp() {
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(cycle(buckets = listOf(bucket("A", "  ", "", " Food ", "Food", "food"))))
        )

        assertEquals(listOf("Food"), restored.single().buckets.single().categories)
    }

    @Test
    fun nonsenseNumbersFallBackToSafeValues() {
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(
                cycle(
                    amount = Double.NaN,
                    buckets = listOf(
                        bucket("A", allocated = -5.0, color = 27),
                        bucket("B", allocated = Double.POSITIVE_INFINITY, color = -1),
                        bucket("C", allocated = null, color = null)
                    )
                )
            )
        )

        val only = restored.single()
        assertEquals(0.0, only.spendableAmount, 0.0)
        assertEquals(listOf(0.0, 0.0, 0.0), only.buckets.map { it.allocatedAmount })
        assertTrue(only.buckets.all { it.colorIndex in 0 until BudgetBucket.COLOR_COUNT })
    }

    @Test
    fun missingSortOrderFallsBackToPositionInTheSheet() {
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(cycle(buckets = listOf(bucket("A"), bucket("B"), bucket("C"))))
        )

        assertEquals(listOf(0, 1, 2), restored.single().buckets.map { it.sortOrder })
    }

    @Test
    fun nullListsAndFieldsFromAHalfEmptySheetDoNotCrash() {
        val restored = BucketBackupMapper.fromImportDtos(listOf(cycle(buckets = null), CycleImportDto()))

        assertEquals(1, restored.size)
        assertTrue(restored.single().buckets.isEmpty())
    }

    // ── closed versus running ─────────────────────────────────────────────────

    @Test
    fun blankClosedAtMeansRunningAndADateMeansClosed() {
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(
                cycle(start = "2026-08-01", end = "2026-08-31", closedAt = "2026-09-01"),
                cycle(closedAt = "  ")
            )
        )

        assertEquals("2026-09-01", restored[0].closedAt)
        assertNull(restored[1].closedAt)
    }

    @Test
    fun aClosedAtThatIsNotADateStillMeansClosedRatherThanReopeningTheCycle() {
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(cycle(start = "2026-08-01", end = "2026-08-31", closedAt = "closed!"), cycle())
        )

        assertEquals("2026-08-31", restored[0].closedAt)
    }

    @Test
    fun onlyTheNewestCycleCanComeBackRunning() {
        val restored = BucketBackupMapper.fromImportDtos(
            listOf(
                cycle(start = "2026-07-01", end = "2026-07-31"),
                cycle(start = "2026-09-01", end = "2026-09-30"),
                cycle(start = "2026-08-01", end = "2026-08-31")
            )
        )

        assertEquals(listOf("2026-07-01", "2026-08-01", "2026-09-01"), restored.map { it.startDate })
        assertEquals("2026-07-31", restored[0].closedAt) // closed at its own end date
        assertEquals("2026-08-31", restored[1].closedAt)
        assertNull(restored[2].closedAt)
        assertEquals(1, restored.count { it.closedAt == null })
    }

    // ── fromSeed ───────────────────────────────────────────────────────────────

    @Test
    fun seedingFromOldBudgetsGivesARunningCycleWithOneCategoryPerBucket() {
        val plan = BucketBudgetMigration.plan(
            listOf(
                BucketBudgetMigration.LegacyBudget("2026-09", "__TOTAL__", 70000.0),
                BucketBudgetMigration.LegacyBudget("2026-09", "Food", 6000.0),
                BucketBudgetMigration.LegacyBudget("2026-09", "Rent", 18000.0)
            )
        )!!

        val restored = BucketBackupMapper.fromSeed(plan)

        assertNull(restored.closedAt)
        assertEquals(70000.0, restored.spendableAmount, 0.0)
        assertEquals(listOf("Food", "Rent"), restored.buckets.map { it.name })
        assertEquals(listOf(listOf("Food"), listOf("Rent")), restored.buckets.map { it.categories })
    }

    private fun CycleSyncDto.asImport() = CycleImportDto(
        id = id, startDate = startDate, endDate = endDate, spendableAmount = spendableAmount, closedAt = closedAt,
        buckets = buckets.map {
            BucketImportDto(
                id = it.id, name = it.name, note = it.note, colorIndex = it.colorIndex, emoji = it.emoji,
                allocatedAmount = it.allocatedAmount, sortOrder = it.sortOrder, categories = it.categories
            )
        }
    )
}
