package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeBudgets(monthYear: String): Flow<List<Budget>>
    suspend fun upsert(record: Budget): Long
    suspend fun delete(record: Budget)
    suspend fun getAllBudgetsSnapshot(): List<Budget>
    suspend fun overwriteAllBudgets(budgets: List<Budget>)
}
