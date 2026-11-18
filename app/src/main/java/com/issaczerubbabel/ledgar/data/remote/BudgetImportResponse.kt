package com.issaczerubbabel.ledgar.data.remote

data class BudgetImportResponse(
    val status: String? = null,
    val data: List<BudgetImportDto>? = null,
    val message: String? = null,
    /** The list's revision (ADR-0010); missing from scripts older than version 3. */
    val revision: String? = null
)
