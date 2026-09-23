package com.issaczerubbabel.ledgar.data.remote

/**
 * Response payload returned by Apps Script doPost.
 */
data class SyncResponse(
    val status: String? = null,
    val count: Int? = null,
    val message: String? = null
)
