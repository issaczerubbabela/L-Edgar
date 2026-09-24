package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One salary cycle: the money the user has to spend between two paydays.
 *
 * A cycle with a null [closedAt] is the running one. It stays running past [endDate]
 * until the user starts the next cycle, so spend logged after payday is never orphaned.
 */
@Entity(tableName = "budget_cycles")
data class BudgetCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO yyyy-MM-dd, matching ExpenseRecord.date. */
    val startDate: String,
    /** ISO yyyy-MM-dd. */
    val endDate: String,
    val spendableAmount: Double,
    /** ISO yyyy-MM-dd the cycle was closed on, or null while it is still running. */
    val closedAt: String? = null
)
