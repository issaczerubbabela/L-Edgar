package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.data.bucket.CycleTransitions
import com.issaczerubbabel.ledgar.data.bucket.StartCycleRequest
import com.issaczerubbabel.ledgar.data.bucket.StartCycleResult
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.BucketBudgetDao
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import kotlinx.coroutines.flow.Flow
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
            val oldAssignments = running?.let { dao.getCategoryAssignments(it.id) }.orEmpty()

            running?.let { dao.updateCycle(CycleTransitions.closeForNext(it, request, today)) }

            val newCycleId = dao.insertCycle(
                BudgetCycle(
                    startDate = request.startDate.toString(),
                    endDate = request.endDate.toString(),
                    spendableAmount = request.spendableAmount
                )
            )

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
            }

            StartCycleResult.Started(newCycleId)
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

    override suspend fun assignCategory(cycleId: Long, bucketId: Long, category: String) {
        dao.assignCategory(BucketCategory(cycleId = cycleId, bucketId = bucketId, category = category.trim()))
    }

    override suspend fun unassignCategory(cycleId: Long, category: String) =
        dao.unassignCategory(cycleId, category.trim())
}
