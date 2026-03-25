package com.sheetsync.data.local.dao

import androidx.room.*
import com.sheetsync.data.local.entity.ExpenseRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ExpenseRecord): Long

    @Query("SELECT * FROM expense_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records WHERE type = :type ORDER BY date DESC")
    fun getByType(type: String): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<ExpenseRecord>

    @Query("UPDATE expense_records SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Delete
    suspend fun delete(record: ExpenseRecord)
}
