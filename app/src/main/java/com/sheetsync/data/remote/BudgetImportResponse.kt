package com.sheetsync.data.remote

data class BudgetImportResponse(
    val status: String? = null,
    val data: List<BudgetImportDto>? = null,
    val message: String? = null
)
