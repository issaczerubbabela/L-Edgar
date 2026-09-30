package com.issaczerubbabel.ledgar.data.remote

data class DropdownImportDto(
    val id: Long? = null,
    val optionType: String,
    val name: String,
    val displayOrder: Int,
    /** Missing when the Sheet was backed up by a script from before Stats roles (ADR-0004). */
    val role: String? = null
)
