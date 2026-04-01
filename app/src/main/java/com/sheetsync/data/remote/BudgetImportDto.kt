package com.sheetsync.data.remote

data class BudgetImportDto(
    val id: Long? = null,
    val monthYear: String,
    val category: String,
    val amount: Double
)
