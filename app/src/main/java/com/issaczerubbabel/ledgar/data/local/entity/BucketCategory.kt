package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Routes one expense category into one bucket for one cycle.
 *
 * The unique index on (cycleId, category) is what enforces "a category lives in exactly one
 * bucket": spend can never be counted twice, so bucket totals always reconcile. cycleId is
 * repeated here (rather than only reachable through the bucket) because a unique index cannot
 * span tables; the composite foreign key guarantees it always agrees with the bucket's own.
 *
 * Categories with no row here are "Unbucketed", a virtual bucket that is never stored.
 */
@Entity(
    tableName = "bucket_categories",
    foreignKeys = [
        ForeignKey(
            entity = BudgetBucket::class,
            parentColumns = ["id", "cycleId"],
            childColumns = ["bucketId", "cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cycleId", "category"], unique = true),
        Index(value = ["bucketId", "cycleId"])
    ]
)
data class BucketCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val bucketId: Long,
    // Category names are free text and the rest of the app compares them ignoring case,
    // so the uniqueness rule has to as well or "Food" and "food" would both be assignable.
    @ColumnInfo(collate = ColumnInfo.NOCASE) val category: String
)
