package com.sheetsync.data.remote

data class BudgetSyncDto(
    val id: Long,
    val monthYear: String,
    val category: String,
    val amount: Double
)
