package com.issaczerubbabel.ledgar.data.remote

data class AccountImportDto(
    val groupName: String,
    val accountName: String,
    val initialBalance: Double,
    val initialBalanceDate: String = "1970-01-01",
    val currentBalance: Double? = null,
    val isHidden: Boolean = false,
    val displayOrder: Int? = null,
    val description: String? = null,
    val includeInTotals: Boolean = true
)
