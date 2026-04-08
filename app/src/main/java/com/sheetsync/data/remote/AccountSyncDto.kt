package com.sheetsync.data.remote

data class AccountSyncDto(
    val id: Long,
    val groupName: String,
    val accountName: String,
    val initialBalance: Double,
    val initialBalanceDate: String,
    val currentBalance: Double? = null,
    val isHidden: Boolean,
    val displayOrder: Int = 0,
    val description: String? = null,
    val includeInTotals: Boolean = true
)
