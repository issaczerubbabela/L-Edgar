package com.sheetsync.data.remote

data class AccountImportDto(
    val groupName: String,
    val accountName: String,
    val initialBalance: Double,
    val isHidden: Boolean = false,
    val displayOrder: Int? = null
)
