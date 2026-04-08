package com.sheetsync.data.remote

/**
 * DTO received from the Apps Script doGet endpoint.
 * Mirror of [SyncRecordDto] but used for the inbound import direction.
 */
data class ImportRecordDto(
    val timestamp: String? = null,
    val date: String,
    val type: String,
    val expCategory: String? = null,
    val incCategory: String? = null,
    val description: String,
    val amount: Double,
    val paymentMode: String? = null,
    val accountName: String? = null,
    val remarks: String,
    val isBookmarked: Boolean? = false,
    val syncedAt: String? = null
)
