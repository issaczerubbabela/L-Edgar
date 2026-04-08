package com.sheetsync.data.remote

/**
 * DTO sent to Google Apps Script.
 * Mirrors the original Google Form columns exactly:
 * - expCategory is set when type == "Expense", incCategory is blank.
 * - incCategory is set when type == "Income", expCategory is blank.
 * - date is always ISO-8601 (YYYY-MM-DD), which Sheets auto-recognises.
 */
data class SyncRecordDto(
    val id: Long,
    val remoteTimestamp: String? = null,
    val timestamp: String = "",
    val date: String,        // YYYY-MM-DD — Google Sheets parses this natively
    val type: String,        // "Expense" | "Income"
    val expCategory: String, // Expense Category column (empty when Income)
    val incCategory: String, // Income Category column (empty when Expense)
    val description: String,
    val amount: Double,
    val accountName: String,
    val remarks: String,
    val isBookmarked: Boolean
)
