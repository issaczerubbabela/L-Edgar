package com.issaczerubbabel.ledgar.data.remote

data class AccountImportResponse(
    val status: String? = null,
    val data: List<AccountImportDto>? = null,
    val message: String? = null
)
