package com.issaczerubbabel.ledgar.data.remote

data class SyncRequest(
	val action: String,
	val target: String = "transactions",
	val records: List<Any> = emptyList(),
	val targetTimestamp: String? = null,
	val allowEmptyBackup: Boolean = false,
	/**
	 * Bucket-budget cycles. Sent here rather than in [records], which is left empty: a script that
	 * predates this field files records as transactions, and would add junk rows to the sheet.
	 */
	val cycles: List<CycleSyncDto>? = null
)
