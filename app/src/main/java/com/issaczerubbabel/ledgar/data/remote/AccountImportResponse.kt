package com.issaczerubbabel.ledgar.data.remote

data class AccountImportResponse(
    val status: String? = null,
    val data: List<AccountImportDto>? = null,
    val message: String? = null,
    /** The list's revision (ADR-0010); missing from scripts older than version 3. */
    val revision: String? = null
)
