package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bank or UPI alert whose amount was visible but which no parser could read, kept so a changed
 * bank wording gets noticed and fixed. Only alerts from watched apps or bank-style SMS sender IDs
 * with an amount are kept, and they are purged after 30 days.
 */
@Entity(
    tableName = "unparsed_alerts",
    indices = [
        Index(value = ["rawHash"], unique = true),
        Index(value = ["capturedAt"])
    ]
)
data class UnparsedAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val rawText: String,
    /** Same-alert dedupe, built like a capture's so a re-read of the shade adds nothing. */
    val rawHash: String,
    val capturedAt: Long
)
