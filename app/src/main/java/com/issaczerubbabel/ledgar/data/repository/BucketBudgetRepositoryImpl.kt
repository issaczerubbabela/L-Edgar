package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.dao.BucketBudgetDao
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BucketBudgetRepositoryImpl @Inject constructor(
    private val dao: BucketBudgetDao
) : BucketBudgetRepository {

    override fun observeRunningCycle(): Flow<BudgetCycle?> = dao.observeRunningCycle()

    override suspend fun getRunningCycle(): BudgetCycle? = dao.getRunningCycle()

    override fun observeAllCycles(): Flow<List<BudgetCycle>> = dao.observeAllCycles()

    override suspend fun getCycle(cycleId: Long): BudgetCycle? = dao.getCycle(cycleId)

    override suspend fun insertCycle(cycle: BudgetCycle): Long = dao.insertCycle(cycle)

    override suspend fun updateCycle(cycle: BudgetCycle) = dao.updateCycle(cycle)

    override suspend fun closeCycle(cycleId: Long, closedAt: String) = dao.closeCycle(cycleId, closedAt)

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
