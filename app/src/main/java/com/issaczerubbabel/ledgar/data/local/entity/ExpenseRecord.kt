package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_records",
    foreignKeys = [
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["fromAccountId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("accountId"), Index("fromAccountId"), Index("toAccountId"), Index(value = ["syncId"], unique = true)]
)
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,           // yyyy-MM-dd
    val type: String,           // "Expense" | "Income" | "Transfer"
    val category: String,
    val description: String,
    val amount: Double,
    val accountId: Long? = null,
    val remarks: String,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val accountName: String? = null,
    val fromAccountName: String? = null,
    val toAccountName: String? = null,
    val isBookmarked: Boolean = false,
    val isSynced: Boolean = false,
    val remoteTimestamp: String? = null,
    val syncAction: String = "INSERT",
    /** Raised by the [ExpenseTableTriggers] on every change; Sync only settles a row whose version it sent. */
    @ColumnInfo(defaultValue = "0") val localVersion: Long = 0,
    /** The Transaction ID shared with the Sheet's ID column. Null only until Sync links an older row. */
    val syncId: String? = null,
    /** The Sheet row's revision when both sides last agreed: the base for merging. */
    val syncedRevision: String? = null,
    /** The Sheet's version as JSON while this Transaction is in a Sync conflict; null otherwise. */
    val sheetConflictJson: String? = null
) {
    @Ignore
    val paymentMode: String = ""
}
