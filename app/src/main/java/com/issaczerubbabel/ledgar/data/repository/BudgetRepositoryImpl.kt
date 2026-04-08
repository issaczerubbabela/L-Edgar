package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.dao.BudgetDao
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao
) : BudgetRepository {
    override fun observeBudgets(monthYear: String): Flow<List<Budget>> = dao.getBudgetsByMonth(monthYear)

    override suspend fun upsert(record: Budget): Long = dao.upsert(record)

    override suspend fun delete(record: Budget) = dao.delete(record)

    override suspend fun getAllBudgetsSnapshot(): List<Budget> = dao.getAllBudgetsSnapshot()

    override suspend fun overwriteAllBudgets(budgets: List<Budget>) {
        dao.clearAll()
        if (budgets.isNotEmpty()) {
            dao.insertAll(budgets)
        }
    }
}
