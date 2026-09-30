package com.issaczerubbabel.ledgar.data.remote

/**
 * DTO received from the Apps Script doGet endpoint.
 * Mirror of [SyncRecordDto] but used for the inbound import direction.
 */
data class ImportRecordDto(
    /** Transaction ID from the Sheet's ID column (scripts from version 2 on). */
    val id: String? = null,
    /** Hash of the row's stored content cells: changes whenever the row is edited, by hand or not. */
    val revision: String? = null,
    val timestamp: String? = null,
    val date: String,
    val type: String,
    val expCategory: String? = null,
    val incCategory: String? = null,
    val description: String,
    val amount: Double,
    val paymentMode: String? = null,
    val accountName: String? = null,
    val fromAccountName: String? = null,
    val toAccountName: String? = null,
    val remarks: String,
    val isBookmarked: Boolean? = false,
    val syncedAt: String? = null
)
