package com.sheetsync.data.remote

/**
 * Wrapper returned by Apps Script doGet, e.g. { status, data, message }.
 */
data class ImportResponse(
    val status: String? = null,
    val data: List<ImportRecordDto>? = null,
    val message: String? = null
)
