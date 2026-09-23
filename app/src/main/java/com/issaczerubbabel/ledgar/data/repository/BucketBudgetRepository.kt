package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.bucket.StartCycleRequest
import com.issaczerubbabel.ledgar.data.bucket.StartCycleResult
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface BucketBudgetRepository {
    // Cycles
    fun observeRunningCycle(): Flow<BudgetCycle?>
    fun observeCycle(cycleId: Long): Flow<BudgetCycle?>
    suspend fun getRunningCycle(): BudgetCycle?
    fun observeAllCycles(): Flow<List<BudgetCycle>>
    suspend fun getCycle(cycleId: Long): BudgetCycle?
    suspend fun insertCycle(cycle: BudgetCycle): Long
    suspend fun updateCycle(cycle: BudgetCycle)
    suspend fun closeCycle(cycleId: Long, closedAt: String)

    /**
     * Ends the running cycle and begins a new one, atomically: either the old cycle is closed,
     * the new one opened and (optionally) the buckets carried over, or nothing changes at all.
     */
    suspend fun startCycle(request: StartCycleRequest, today: LocalDate = LocalDate.now()): StartCycleResult

    // Buckets
    fun observeBucket(bucketId: Long): Flow<BudgetBucket?>
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

    /**
     * Makes [categories] exactly the set routed into this bucket: anything else it held is released
     * to Unbucketed, and anything listed is moved in from whichever bucket held it.
     */
    suspend fun setBucketCategories(cycleId: Long, bucketId: Long, categories: Set<String>)
}
