package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The Trip tables (see "Trips" in CONTEXT.md). Local-only like the auto-capture tables: never
 * synced, backed up or imported. Only Post writes to the ledger, as ordinary [ExpenseRecord]s
 * (ADR-0008). Amounts are whole paise.
 */
@Entity(tableName = "trips")
data class TripRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String, // yyyy-MM-dd
    val endDate: String? = null,
    val status: String = STATUS_ACTIVE,
    val postedAt: Long? = null
) {
    val isArchived: Boolean get() = status == STATUS_ARCHIVED

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_ARCHIVED = "ARCHIVED"
    }
}

@Entity(
    tableName = "trip_members",
    foreignKeys = [ForeignKey(entity = TripRecord::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class TripMemberRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val isSelf: Boolean = false,
    val upiId: String? = null,
    val displayOrder: Int = 0,
    /** Index into the Member palette; stored so removing someone never shifts the others' colours. */
    @ColumnInfo(defaultValue = "0") val colorIndex: Int = 0
)

@Entity(
    tableName = "trip_expenses",
    foreignKeys = [ForeignKey(entity = TripRecord::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class TripExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val date: String, // yyyy-MM-dd
    val purpose: String,
    val amountPaise: Long,
    val payerMemberId: Long,
    val splitMode: String, // EQUAL | CUSTOM (ADJUST and EXACT before version 23)
    val category: String = "",
    /** The [ExpenseRecord] this expense's Share became when the Trip was Posted. */
    val postedExpenseId: Long? = null
)

@Entity(
    tableName = "trip_expense_shares",
    primaryKeys = ["expenseId", "memberId"],
    foreignKeys = [ForeignKey(entity = TripExpenseRecord::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("memberId")]
)
data class TripExpenseShareRecord(
    val expenseId: Long,
    val memberId: Long,
    /** The Member's Locked amount when [locked]; 0 otherwise. */
    val inputPaise: Long,
    val sharePaise: Long,
    /** True when the user typed this Member's amount in a Custom split. */
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false
)

@Entity(
    tableName = "trip_settlements",
    foreignKeys = [ForeignKey(entity = TripRecord::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class TripSettlementRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val fromMemberId: Long,
    val toMemberId: Long,
    val amountPaise: Long,
    val date: String
)
