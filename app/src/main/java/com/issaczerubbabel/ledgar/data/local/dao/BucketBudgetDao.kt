package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import kotlinx.coroutines.flow.Flow

@Dao
interface BucketBudgetDao {

    // ── Cycles ────────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertCycle(cycle: BudgetCycle): Long

    @Update
    suspend fun updateCycle(cycle: BudgetCycle)

    @Query("SELECT * FROM budget_cycles WHERE id = :cycleId")
    suspend fun getCycle(cycleId: Long): BudgetCycle?

    /** The running cycle: the newest one that has not been closed. */
    @Query("SELECT * FROM budget_cycles WHERE closedAt IS NULL ORDER BY startDate DESC, id DESC LIMIT 1")
    fun observeRunningCycle(): Flow<BudgetCycle?>

    @Query("SELECT * FROM budget_cycles WHERE closedAt IS NULL ORDER BY startDate DESC, id DESC LIMIT 1")
    suspend fun getRunningCycle(): BudgetCycle?

    /** Every cycle, newest first, for the history view. */
    @Query("SELECT * FROM budget_cycles ORDER BY startDate DESC, id DESC")
    fun observeAllCycles(): Flow<List<BudgetCycle>>

    @Query("UPDATE budget_cycles SET closedAt = :closedAt WHERE id = :cycleId")
    suspend fun closeCycle(cycleId: Long, closedAt: String)

    // ── Buckets ───────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertBucket(bucket: BudgetBucket): Long

    @Insert
    suspend fun insertBuckets(buckets: List<BudgetBucket>): List<Long>

    @Update
    suspend fun updateBucket(bucket: BudgetBucket)

    @Delete
    suspend fun deleteBucket(bucket: BudgetBucket)

    @Query("SELECT * FROM budget_buckets WHERE cycleId = :cycleId ORDER BY sortOrder ASC, id ASC")
    fun observeBuckets(cycleId: Long): Flow<List<BudgetBucket>>

    @Query("SELECT * FROM budget_buckets WHERE cycleId = :cycleId ORDER BY sortOrder ASC, id ASC")
    suspend fun getBuckets(cycleId: Long): List<BudgetBucket>

    // ── Category routing ──────────────────────────────────────────────────────

    /**
     * Puts a category into a bucket. REPLACE on the unique (cycleId, category) index means
     * assigning a category that already lives in another bucket moves it, rather than failing.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assignCategory(assignment: BucketCategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assignCategories(assignments: List<BucketCategory>)

    @Query("DELETE FROM bucket_categories WHERE cycleId = :cycleId AND category = :category")
    suspend fun unassignCategory(cycleId: Long, category: String)

    @Query("SELECT * FROM bucket_categories WHERE cycleId = :cycleId ORDER BY category ASC")
    fun observeCategoryAssignments(cycleId: Long): Flow<List<BucketCategory>>

    @Query("SELECT * FROM bucket_categories WHERE cycleId = :cycleId ORDER BY category ASC")
    suspend fun getCategoryAssignments(cycleId: Long): List<BucketCategory>
}
