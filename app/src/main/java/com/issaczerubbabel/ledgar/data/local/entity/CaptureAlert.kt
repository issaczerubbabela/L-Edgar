package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Alert a [CapturedTransaction] was read from. A capture has one or more: Alerts from
 * different Alert sources about one payment are merged into one capture (ADR-0012). Local only,
 * never synced; deleting the capture deletes its Alerts.
 */
@Entity(
    tableName = "capture_alerts",
    foreignKeys = [
        ForeignKey(
            entity = CapturedTransaction::class,
            parentColumns = ["id"],
            childColumns = ["captureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["rawHash"], unique = true),
        Index(value = ["captureId"]),
        Index(value = ["capturedAt"])
    ]
)
data class CaptureAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val captureId: Long,
    /** How it was read: NOTIF, SMS or EMAIL (`CaptureSource`). With [sender] it gives the Alert source. */
    val source: String,
    val sender: String,
    val rawText: String,
    /** The Alert's dedupe key; the unique index drops a repeat of any Alert, merged or not. */
    val rawHash: String,
    /** When it arrived (the notification's post time). */
    val capturedAt: Long
)
