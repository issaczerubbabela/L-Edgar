package com.issaczerubbabel.ledgar.data.remote

/** Payload used by Apps Script to delete a transaction by remote timestamp. */
data class DeletePayload(
    val target: String = "transactions",
    val action: String = "delete",
    val targetTimestamp: String
)