package com.sheetsync.data.remote

data class DropdownSyncDto(
    val id: Long,
    val optionType: String,
    val name: String,
    val displayOrder: Int
)
