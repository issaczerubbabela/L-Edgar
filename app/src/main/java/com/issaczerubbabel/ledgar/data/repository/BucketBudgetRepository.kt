package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import kotlinx.coroutines.flow.Flow

interface BucketBudgetRepository {
    // Cycles
    fun observeRunningCycle(): Flow<BudgetCycle?>
    suspend fun getRunningCycle(): BudgetCycle?
    fun observeAllCycles(): Flow<List<BudgetCycle>>
    suspend fun getCycle(cycleId: Long): BudgetCycle?
    suspend fun insertCycle(cycle: BudgetCycle): Long
    suspend fun updateCycle(cycle: BudgetCycle)
    suspend fun closeCycle(cycleId: Long, closedAt: String)

    // Buckets
    fun observeBuckets(cycleId: Long): Flow<List<BudgetBucket>>
    suspend fun getBuckets(cycleId: Long): List<BudgetBucket>
    suspend fun insertBucket(bucket: BudgetBucket): Long
    suspend fun updateBucket(bucket: BudgetBucket)
    suspend fun deleteBucket(bucket: BudgetBucket)

    // Category routing
    fun observeCategoryAssignments(cycleId: Long): Flow<List<BucketCategory>>
    suspend fun getCategoryAssignments(cycleId: Long): List<BucketCategory>

    /** Moves the category into the bucket, taking it out of whichever bucket held it before. */
    suspend fun assignCategory(cycleId: Long, bucketId: Long, category: String)
    suspend fun unassignCategory(cycleId: Long, category: String)
}
