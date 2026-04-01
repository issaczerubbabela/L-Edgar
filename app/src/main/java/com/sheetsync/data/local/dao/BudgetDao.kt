package com.sheetsync.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sheetsync.data.local.entity.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: Budget): Long

    @Update
    suspend fun update(record: Budget)

    @Delete
    suspend fun delete(record: Budget)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<Budget>)

    @Query("DELETE FROM budgets")
    suspend fun clearAll()

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear ORDER BY category ASC")
    fun getBudgetsByMonth(monthYear: String): Flow<List<Budget>>

    @Query("SELECT * FROM budgets ORDER BY monthYear ASC, category ASC")
    suspend fun getAllBudgetsSnapshot(): List<Budget>
}
