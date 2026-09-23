package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration
import com.issaczerubbabel.ledgar.data.remote.BucketSyncDto
import com.issaczerubbabel.ledgar.data.remote.CycleImportDto
import com.issaczerubbabel.ledgar.data.remote.CycleSyncDto
import java.time.LocalDate

/** Every cycle, bucket and category routing on the device, read together. */
data class BucketBudgetSnapshot(
    val cycles: List<BudgetCycle>,
    val buckets: List<BudgetBucket>,
    val assignments: List<BucketCategory>
)

/** A bucket ready to be written back, with no database ids: those are assigned on insert. */
data class RestoredBucket(
    val name: String,
    val note: String,
    val colorIndex: Int,
    val emoji: String,
    val allocatedAmount: Double,
    val sortOrder: Int,
    val categories: List<String>
)

data class RestoredCycle(
    val startDate: String,
    val endDate: String,
    val spendableAmount: Double,
    val closedAt: String?,
    val buckets: List<RestoredBucket>
)

/**
 * Converts between the database rows and the nested shape stored in Google Sheets. Pure, so both
 * directions are tested without a database or a network.
 *
 * Restoring is the dangerous direction: the sheet can be edited by hand, so everything read from
 * it is treated as untrusted and cleaned until it cannot violate a database constraint.
 */
object BucketBackupMapper {

    fun toSyncDtos(snapshot: BucketBudgetSnapshot): List<CycleSyncDto> {
        val bucketsByCycle = snapshot.buckets.groupBy { it.cycleId }
        val categoriesByBucket = snapshot.assignments.groupBy { it.bucketId }

        return snapshot.cycles
            .sortedWith(compareBy<BudgetCycle> { it.startDate }.thenBy { it.id })
            .map { cycle ->
                CycleSyncDto(
                    id = cycle.id,
                    startDate = cycle.startDate,
                    endDate = cycle.endDate,
                    spendableAmount = cycle.spendableAmount,
                    closedAt = cycle.closedAt,
                    buckets = bucketsByCycle[cycle.id].orEmpty()
                        .sortedWith(compareBy<BudgetBucket> { it.sortOrder }.thenBy { it.id })
                        .map { bucket ->
                            BucketSyncDto(
                                id = bucket.id,
                                name = bucket.name,
                                note = bucket.note,
                                colorIndex = bucket.colorIndex,
                                emoji = bucket.emoji,
                                allocatedAmount = bucket.allocatedAmount,
                                sortOrder = bucket.sortOrder,
                                categories = categoriesByBucket[bucket.id].orEmpty().map { it.category }.sorted()
                            )
                        }
                )
            }
    }

    fun fromImportDtos(dtos: List<CycleImportDto>): List<RestoredCycle> {
        val cycles = dtos.mapNotNull { dto ->
            val start = parseDate(dto.startDate) ?: return@mapNotNull null
            val end = parseDate(dto.endDate) ?: return@mapNotNull null
            if (end.isBefore(start)) return@mapNotNull null

            // A category may sit in only one bucket per cycle, ignoring case: the database enforces
            // it with a unique index, so a duplicate in the sheet has to be dropped here rather
            // than fail the whole restore. The first bucket to claim a category keeps it.
            val claimed = HashSet<String>()
            val buckets = dto.buckets.orEmpty().mapIndexedNotNull { index, bucket ->
                val name = bucket.name?.trim().orEmpty()
                if (name.isEmpty()) return@mapIndexedNotNull null
                RestoredBucket(
                    name = name,
                    note = bucket.note?.trim().orEmpty(),
                    colorIndex = Math.floorMod(bucket.colorIndex ?: BudgetBucket.colorIndexFor(name), BudgetBucket.COLOR_COUNT),
                    emoji = bucket.emoji?.trim().orEmpty(),
                    allocatedAmount = bucket.allocatedAmount?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                    sortOrder = bucket.sortOrder ?: index,
                    categories = bucket.categories.orEmpty()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && claimed.add(it.lowercase()) }
                )
            }

            RestoredCycle(
                startDate = start.toString(),
                endDate = end.toString(),
                spendableAmount = dto.spendableAmount?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                // Blank means still running. Something written there that is not a date still means
                // "closed", so it falls back to the cycle's end date rather than reopening it.
                closedAt = dto.closedAt?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { parseDate(it)?.toString() ?: end.toString() },
                buckets = buckets
            )
        }

        return onlyOneRunning(cycles).sortedBy { it.startDate }
    }

    /** The first cycle a fresh install gets when only the older monthly budgets could be restored. */
    fun fromSeed(seed: BucketBudgetMigration.SeedCycle): RestoredCycle = RestoredCycle(
        startDate = seed.startDate,
        endDate = seed.endDate,
        spendableAmount = seed.spendableAmount,
        closedAt = null,
        buckets = seed.buckets.map { bucket ->
            RestoredBucket(
                name = bucket.name,
                note = "",
                colorIndex = bucket.colorIndex,
                emoji = "",
                allocatedAmount = bucket.allocatedAmount,
                sortOrder = bucket.sortOrder,
                categories = listOf(bucket.name)
            )
        }
    )

    /**
     * Nothing else expects two running cycles: the newest one stays running and any older one that
     * claims to still be running is closed at its own end date.
     */
    private fun onlyOneRunning(cycles: List<RestoredCycle>): List<RestoredCycle> {
        val running = cycles.filter { it.closedAt == null }.maxByOrNull { it.startDate }
        return cycles.map { cycle ->
            if (cycle.closedAt == null && cycle !== running) cycle.copy(closedAt = cycle.endDate) else cycle
        }
    }

    private fun parseDate(raw: String?): LocalDate? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
