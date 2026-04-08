package com.issaczerubbabel.ledgar.data.remote

data class DropdownImportResponse(
    val status: String? = null,
    val data: List<DropdownImportDto>? = null,
    val message: String? = null
)
