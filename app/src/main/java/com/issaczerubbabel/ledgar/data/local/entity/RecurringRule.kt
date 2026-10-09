package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A template for a Transaction that recreates itself on a schedule (rent, SIPs, salary). Every rule
 * here is automatic: it always creates its occurrence, with no "ask me first" approval step or
 * management screen yet.
 *
 * Deleting the account a rule points at nulls that column (same as [ExpenseRecord]); a rule with a
 * missing required account is skipped by materialization until it is fixed, rather than creating a
 * broken Transaction.
 */
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(entity = AccountRecord::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = AccountRecord::class, parentColumns = ["id"], childColumns = ["fromAccountId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = AccountRecord::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("accountId"), Index("fromAccountId"), Index("toAccountId"), Index("nextDate")]
)
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,           // "Expense" | "Income" | "Transfer"
    val category: String,
    val description: String,
    val amount: Double,
    val accountId: Long? = null,
    val remarks: String,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val frequency: String,      // WEEKLY | MONTHLY | YEARLY, see RecurrenceFrequency
    val interval: Int = 1,
    /** Day of month (1-31) for MONTHLY/YEARLY, day of week (1=Monday..7=Sunday) for WEEKLY. */
    val anchorDay: Int,
    val startDate: String,      // yyyy-MM-dd, the date the rule was created from
    val nextDate: String,       // yyyy-MM-dd, the next occurrence still to materialize
    val endDate: String? = null,
    val remainingCount: Int? = null,
    val autoAdd: Boolean = true,
    val isPaused: Boolean = false,
    val createdAt: String
)
