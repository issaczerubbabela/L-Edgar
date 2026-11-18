package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.data.bucket.BucketBudgetSnapshot
import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
import com.issaczerubbabel.ledgar.data.bucket.CycleTransitions
import com.issaczerubbabel.ledgar.data.bucket.RestoredCycle
import com.issaczerubbabel.ledgar.data.bucket.StartCycleRequest
import com.issaczerubbabel.ledgar.data.bucket.StartCycleResult
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.BucketBudgetDao
import com.issaczerubbabel.ledgar.data.local.entity.BucketAccount
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.bucket.TrackedAccounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

class BucketBudgetRepositoryImpl @Inject constructor(
    private val db: SheetSyncDatabase,
    private val dao: BucketBudgetDao
) : BucketBudgetRepository {

    override fun observeRunningCycle(): Flow<BudgetCycle?> = dao.observeRunningCycle()

    override suspend fun getRunningCycle(): BudgetCycle? = dao.getRunningCycle()

    override fun observeAllCycles(): Flow<List<BudgetCycle>> = dao.observeAllCycles()

    override suspend fun getCycle(cycleId: Long): BudgetCycle? = dao.getCycle(cycleId)

    override suspend fun insertCycle(cycle: BudgetCycle): Long = dao.insertCycle(cycle)

    override suspend fun updateCycle(cycle: BudgetCycle) = dao.updateCycle(cycle)

    override suspend fun closeCycle(cycleId: Long, closedAt: String) = dao.closeCycle(cycleId, closedAt)

    override suspend fun startCycle(request: StartCycleRequest, today: LocalDate): StartCycleResult =
        db.withTransaction {
            val running = dao.getRunningCycle()
            CycleTransitions.validate(request, running)?.let { return@withTransaction StartCycleResult.Rejected(it) }

            val oldBuckets = running?.let { dao.getBuckets(it.id) }.orEmpty()
            // A Category deleted from the list isn't carried into the new cycle's Buckets.
            val current = db.dropdownOptionDao().getAllOptionsSnapshot()
                .filter { it.optionType == EXPENSE_CATEGORY }
                .map { CycleSummaryBuilder.categoryKey(it.name) }
                .toSet()
            val oldAssignments = running?.let { dao.getCategoryAssignments(it.id) }.orEmpty()
                .filter { current.isEmpty() || CycleSummaryBuilder.categoryKey(it.category) in current }

            running?.let { dao.updateCycle(CycleTransitions.closeForNext(it, request, today)) }

            val newCycleId = dao.insertCycle(
                BudgetCycle(
                    startDate = request.startDate.toString(),
                    endDate = request.endDate.toString(),
                    spendableAmount = request.spendableAmount
                )
            )

            val oldAccountRoutes = running?.let { dao.getAccountRoutes(it.id) }.orEmpty()

            if (request.carryOverBuckets && oldBuckets.isNotEmpty()) {
                val newIdByOldId = HashMap<Long, Long>()
                oldBuckets.forEach { bucket ->
                    newIdByOldId[bucket.id] = dao.insertBucket(bucket.copy(id = 0, cycleId = newCycleId))
                }
                dao.assignCategories(
                    oldAssignments.mapNotNull { assignment ->
                        newIdByOldId[assignment.bucketId]?.let { newBucketId ->
                            BucketCategory(cycleId = newCycleId, bucketId = newBucketId, category = assignment.category)
                        }
                    }
                )
                dao.trackAccounts(
                    oldAccountRoutes.mapNotNull { route ->
                        newIdByOldId[route.bucketId]?.let { BucketAccount(cycleId = newCycleId, bucketId = it, accountId = route.accountId) }
                    }
                )
            }

            StartCycleResult.Started(newCycleId)
        }

    override fun observeCycle(cycleId: Long): Flow<BudgetCycle?> = dao.observeCycle(cycleId)

    override fun observeBucket(bucketId: Long): Flow<BudgetBucket?> = dao.observeBucket(bucketId)

    override suspend fun getBackupSnapshot(): BucketBudgetSnapshot = db.withTransaction {
        BucketBudgetSnapshot(
            cycles = dao.getAllCycles(),
            buckets = dao.getAllBuckets(),
            assignments = dao.getAllAssignments(),
            accountRoutes = dao.getAllAccountRoutes(),
            accountNames = db.accountDao().getAllAccountsSnapshot().associate { it.id to it.accountName }
        )
    }

    override suspend fun replaceAllFromBackup(cycles: List<RestoredCycle>) {
        db.withTransaction {
            // Accounts are matched by name, ignoring case; a name with no Account is dropped.
            val accountIdByName = db.accountDao().getAllAccountsSnapshot()
                .associate { CycleSummaryBuilder.categoryKey(it.accountName) to it.id }
            dao.deleteAllAssignments()
            dao.deleteAllBuckets()
            dao.deleteAllCycles()
            cycles.forEach { cycle ->
                val cycleId = dao.insertCycle(
                    BudgetCycle(
                        startDate = cycle.startDate,
                        endDate = cycle.endDate,
                        spendableAmount = cycle.spendableAmount,
                        closedAt = cycle.closedAt
                    )
                )
                cycle.buckets.forEach { bucket ->
                    val bucketId = dao.insertBucket(
                        BudgetBucket(
                            cycleId = cycleId,
                            name = bucket.name,
                            note = bucket.note,
                            colorIndex = bucket.colorIndex,
                            emoji = bucket.emoji,
                            allocatedAmount = bucket.allocatedAmount,
                            sortOrder = bucket.sortOrder
                        )
                    )
                    dao.assignCategories(
                        bucket.categories.map { BucketCategory(cycleId = cycleId, bucketId = bucketId, category = it) }
                    )
                    dao.trackAccounts(
                        bucket.accounts
                            .mapNotNull { accountIdByName[CycleSummaryBuilder.categoryKey(it)] }
                            .distinct()
                            .map { BucketAccount(cycleId = cycleId, bucketId = bucketId, accountId = it) }
                    )
                }
            }
        }
    }

    override fun observeBuckets(cycleId: Long): Flow<List<BudgetBucket>> = dao.observeBuckets(cycleId)

    override suspend fun getBuckets(cycleId: Long): List<BudgetBucket> = dao.getBuckets(cycleId)

    override suspend fun insertBucket(bucket: BudgetBucket): Long = dao.insertBucket(bucket)

    override suspend fun updateBucket(bucket: BudgetBucket) = dao.updateBucket(bucket)

    override suspend fun deleteBucket(bucket: BudgetBucket) = dao.deleteBucket(bucket)

    override fun observeCategoryAssignments(cycleId: Long): Flow<List<BucketCategory>> =
        dao.observeCategoryAssignments(cycleId)

    override suspend fun getCategoryAssignments(cycleId: Long): List<BucketCategory> =
        dao.getCategoryAssignments(cycleId)

    override fun observeAccountRoutes(cycleId: Long): Flow<List<BucketAccount>> = dao.observeAccountRoutes(cycleId)

    override fun observeAllAccountRoutes(): Flow<List<BucketAccount>> = dao.observeAllAccountRoutes()

    override fun observeTrackedAccounts(cycleId: Long): Flow<TrackedAccounts> =
        combine(dao.observeAccountRoutes(cycleId), db.accountDao().getAllAccounts(), ::TrackedAccounts)

    override suspend fun setBucketAccounts(cycleId: Long, bucketId: Long, accountIds: Set<Long>) {
        db.withTransaction {
            dao.untrackBucketAccounts(cycleId, bucketId)
            dao.trackAccounts(accountIds.map { BucketAccount(cycleId = cycleId, bucketId = bucketId, accountId = it) })
        }
    }

    override suspend fun assignCategory(cycleId: Long, bucketId: Long, category: String) {
        dao.assignCategory(BucketCategory(cycleId = cycleId, bucketId = bucketId, category = category.trim()))
    }

    override suspend fun unassignCategory(cycleId: Long, category: String) =
        dao.unassignCategory(cycleId, category.trim())

    override suspend fun setBucketCategories(cycleId: Long, bucketId: Long, categories: Set<String>) {
        val wanted = categories.map { it.trim() }.filter { it.isNotBlank() }
        val wantedKeys = wanted.map { it.lowercase() }.toSet()
        db.withTransaction {
            dao.getCategoryAssignments(cycleId)
                .filter { it.bucketId == bucketId && it.category.trim().lowercase() !in wantedKeys }
                .forEach { dao.unassignCategory(cycleId, it.category) }
            dao.assignCategories(wanted.map { BucketCategory(cycleId = cycleId, bucketId = bucketId, category = it) })
        }
    }

    private companion object {
        const val EXPENSE_CATEGORY = "EXPENSE_CATEGORY"
    }
}
