package com.issaczerubbabel.ledgar.data.remote

data class SyncRequest(
	val action: String,
	val target: String = "transactions",
	val records: List<Any> = emptyList(),
	val targetTimestamp: String? = null,
	val allowEmptyBackup: Boolean = false,
	/** Version-2 upserts. Never sent in [records], which older scripts append as transactions. */
	val transactions: List<SheetTransactionDto>? = null,
	/** Transaction IDs for a version-2 delete. */
	val ids: List<String>? = null
)
