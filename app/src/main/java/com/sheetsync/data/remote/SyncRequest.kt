package com.sheetsync.data.remote

data class SyncRequest(
	val action: String,
	val records: List<SyncRecordDto> = emptyList(),
	val targetTimestamp: String? = null
)
