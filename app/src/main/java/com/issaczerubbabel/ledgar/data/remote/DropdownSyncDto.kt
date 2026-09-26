package com.issaczerubbabel.ledgar.data.remote

data class DropdownSyncDto(
    val id: Long,
    val optionType: String,
    val name: String,
    val displayOrder: Int,
    /** Stats role (ADR-0004). A script deployed before roles existed ignores it. */
    val role: String = ""
)
