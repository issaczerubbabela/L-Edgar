package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Makes one Bucket track the Transfers into one Account for one cycle, such as a recurring deposit
 * or a SIP: money moved into the Account from outside the Bucket's Accounts counts as the Bucket's,
 * and money moved back out takes it away.
 *
 * Like [BucketCategory], an Account sits in at most one Bucket per cycle (the unique index), so a
 * Transfer is never counted twice. Deleting the Account or the Bucket removes the row.
 */
@Entity(
    tableName = "bucket_accounts",
    foreignKeys = [
        ForeignKey(
            entity = BudgetBucket::class,
            parentColumns = ["id", "cycleId"],
            childColumns = ["bucketId", "cycleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cycleId", "accountId"], unique = true),
        Index(value = ["bucketId", "cycleId"]),
        Index(value = ["accountId"])
    ]
)
data class BucketAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val bucketId: Long,
    val accountId: Long
)
