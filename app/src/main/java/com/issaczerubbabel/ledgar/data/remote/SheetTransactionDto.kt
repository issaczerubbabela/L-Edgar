package com.issaczerubbabel.ledgar.data.remote

/** One Transaction as sent to a version-2 script's `upsert`, keyed by its Transaction ID. */
data class SheetTransactionDto(
    val id: String,
    val timestamp: String?,
    val date: String,
    val type: String,
    val expCategory: String,
    val incCategory: String,
    val description: String,
    val amount: Double,
    val accountName: String,
    val fromAccountName: String?,
    val toAccountName: String?,
    val remarks: String,
    val isBookmarked: Boolean,
    /** The row revision this change was based on; the script refuses it if the row has changed since. */
    val base: String? = null
)
