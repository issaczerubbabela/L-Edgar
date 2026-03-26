package com.sheetsync.data.repository

import com.sheetsync.data.local.dao.BudgetDao
import com.sheetsync.data.local.entity.BudgetRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao
) : BudgetRepository {
    override fun observeBudgets(): Flow<List<BudgetRecord>> = dao.getAllBudgets()

    override suspend fun upsert(record: BudgetRecord): Long = dao.upsert(record)

    override suspend fun delete(record: BudgetRecord) = dao.delete(record)
}
