package com.sheetsync.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sheetsync.data.local.entity.BudgetRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BudgetRecord): Long

    @Update
    suspend fun update(record: BudgetRecord)

    @Delete
    suspend fun delete(record: BudgetRecord)

    @Query("SELECT * FROM budget_records ORDER BY category ASC")
    fun getAllBudgets(): Flow<List<BudgetRecord>>

    @Query("SELECT * FROM budget_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BudgetRecord?
}
