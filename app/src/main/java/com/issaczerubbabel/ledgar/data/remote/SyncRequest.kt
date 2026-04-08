package com.issaczerubbabel.ledgar.data.remote

data class SyncRequest(
	val action: String,
	val target: String = "transactions",
	val records: List<Any> = emptyList(),
	val targetTimestamp: String? = null
)
