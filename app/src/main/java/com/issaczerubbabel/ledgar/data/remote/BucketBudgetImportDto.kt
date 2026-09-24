package com.issaczerubbabel.ledgar.data.remote

// Everything is nullable with a default: Gson can leave a Kotlin non-null field null when a sheet
// cell was blank, and a restore has to cope with a hand-edited sheet instead of crashing.

data class BucketImportDto(
    val id: Long? = null,
    val name: String? = null,
    val note: String? = null,
    val colorIndex: Int? = null,
    val emoji: String? = null,
    val allocatedAmount: Double? = null,
    val sortOrder: Int? = null,
    val categories: List<String>? = null
)

data class CycleImportDto(
    val id: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val spendableAmount: Double? = null,
    val closedAt: String? = null,
    val buckets: List<BucketImportDto>? = null
)

data class BucketBudgetImportResponse(
    val status: String? = null,
    /**
     * "bucket_budgets" from a script that understands the target. A script deployed before it
     * existed answers with the transaction list instead, which has no such marker.
     */
    val type: String? = null,
    val data: List<CycleImportDto>? = null,
    val message: String? = null
) {
    val isUnderstoodByScript: Boolean get() = type == TYPE

    companion object {
        const val TYPE = "bucket_budgets"
    }
}
