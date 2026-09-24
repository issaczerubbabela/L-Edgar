package com.issaczerubbabel.ledgar.data.remote

/**
 * Response payload returned by Apps Script doPost.
 */
data class SyncResponse(
    val status: String? = null,
    val count: Int? = null,
    val message: String? = null,
    /** Set by scripts that recognise the target, e.g. "bucket_budgets_backed_up". */
    val type: String? = null,
    val action: String? = null
)
