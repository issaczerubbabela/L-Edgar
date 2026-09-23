package com.issaczerubbabel.ledgar.data.remote

/**
 * Response payload returned by Apps Script doPost.
 */
data class SyncResponse(
    val status: String? = null,
    val count: Int? = null,
    val message: String? = null,
    /** Absent from scripts older than version 2, which ignore version-2 requests. */
    val scriptVersion: Int? = null,
    val deleted: Int? = null,
    /** IDs an upsert refused because their row changed since the `base` revision sent. */
    val stale: List<String>? = null,
    /** Each written row's revision as now stored. */
    val revisions: Map<String, String>? = null
)
