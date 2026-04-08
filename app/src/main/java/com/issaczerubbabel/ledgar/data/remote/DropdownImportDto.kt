package com.issaczerubbabel.ledgar.data.remote

data class DropdownImportDto(
    val id: Long? = null,
    val optionType: String,
    val name: String,
    val displayOrder: Int
)
