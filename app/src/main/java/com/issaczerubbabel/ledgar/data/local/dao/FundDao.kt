package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface FundDao {

    // ── Funds ─────────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertFund(fund: Fund): Long

    @Update
    suspend fun updateFund(fund: Fund)

    @Delete
    suspend fun deleteFund(fund: Fund)

    @Query("SELECT * FROM funds WHERE id = :fundId")
    suspend fun getFund(fundId: Long): Fund?

    @Query("SELECT * FROM funds WHERE id = :fundId")
    fun observeFund(fundId: Long): Flow<Fund?>

    @Query("SELECT * FROM funds ORDER BY sortOrder ASC, id ASC")
    fun observeFunds(): Flow<List<Fund>>

    @Query("SELECT * FROM funds ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllFunds(): List<Fund>

    // ── Holdings ──────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertHolding(holding: FundHolding): Long

    @Update
    suspend fun updateHolding(holding: FundHolding)

    @Delete
    suspend fun deleteHolding(holding: FundHolding)

    @Query("SELECT * FROM fund_holdings WHERE id = :holdingId")
    suspend fun getHolding(holdingId: Long): FundHolding?

    @Query("SELECT * FROM fund_holdings ORDER BY fundId ASC, id ASC")
    fun observeHoldings(): Flow<List<FundHolding>>

    @Query("SELECT * FROM fund_holdings ORDER BY fundId ASC, id ASC")
    suspend fun getAllHoldings(): List<FundHolding>

    /** The WHOLE holding of [accountId], whichever Fund has it. */
    @Query("SELECT * FROM fund_holdings WHERE accountId = :accountId AND kind = 'WHOLE' ORDER BY id ASC LIMIT 1")
    suspend fun getWholeHolding(accountId: Long): FundHolding?

    // ── Planned contributions ─────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: FundPlan)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlans(plans: List<FundPlan>)

    @Query("DELETE FROM fund_plans WHERE fundId = :fundId AND cycleId = :cycleId")
    suspend fun deletePlan(fundId: Long, cycleId: Long)

    @Query("SELECT * FROM fund_plans WHERE cycleId = :cycleId ORDER BY fundId ASC")
    fun observePlans(cycleId: Long): Flow<List<FundPlan>>

    @Query("SELECT * FROM fund_plans WHERE cycleId = :cycleId ORDER BY fundId ASC")
    suspend fun getPlans(cycleId: Long): List<FundPlan>

    @Query("SELECT * FROM fund_plans ORDER BY cycleId ASC, fundId ASC")
    fun observeAllPlans(): Flow<List<FundPlan>>

    @Query("SELECT * FROM fund_plans ORDER BY cycleId ASC, fundId ASC")
    suspend fun getAllPlans(): List<FundPlan>

    // ── Whole-table access, for restore ───────────────────────────────────────

    // Children first. Foreign keys would cascade anyway, but this does not depend on them.
    @Query("DELETE FROM fund_plans")
    suspend fun deleteAllPlans()

    @Query("DELETE FROM fund_holdings")
    suspend fun deleteAllHoldings()

    @Query("DELETE FROM funds")
    suspend fun deleteAllFunds()
}
