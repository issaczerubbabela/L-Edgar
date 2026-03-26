package com.sheetsync.data.repository

import com.sheetsync.data.local.entity.BudgetRecord
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeBudgets(): Flow<List<BudgetRecord>>
    suspend fun upsert(record: BudgetRecord): Long
    suspend fun delete(record: BudgetRecord)
}
