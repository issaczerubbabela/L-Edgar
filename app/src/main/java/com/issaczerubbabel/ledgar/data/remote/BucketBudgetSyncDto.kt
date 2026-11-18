package com.issaczerubbabel.ledgar.data.remote

/** One bucket as backed up to the `_buckets` sheet, with the categories routed into it. */
data class BucketSyncDto(
    val id: Long,
    val name: String,
    val note: String,
    val colorIndex: Int,
    val emoji: String,
    val allocatedAmount: Double,
    val sortOrder: Int,
    val categories: List<String>,
    /**
     * Names of the Accounts whose Transfers the Bucket tracks. Left out (null) when there are none,
     * and ignored by scripts older than version 3, which then just don't back them up.
     */
    val accounts: List<String>? = null
)

/**
 * One salary cycle as backed up to the `_cycles` sheet. Buckets and categories are nested so a
 * restore never has to re-link ids across separate tables.
 */
data class CycleSyncDto(
    val id: Long,
    val startDate: String,
    val endDate: String,
    val spendableAmount: Double,
    val closedAt: String?,
    val buckets: List<BucketSyncDto>
)
