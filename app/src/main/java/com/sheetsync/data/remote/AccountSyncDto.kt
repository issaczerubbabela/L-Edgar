package com.sheetsync.data.remote

data class AccountSyncDto(
    val id: Long,
    val groupName: String,
    val accountName: String,
    val initialBalance: Double,
    val isHidden: Boolean
)
