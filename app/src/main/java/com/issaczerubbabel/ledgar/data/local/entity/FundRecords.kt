package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Fund: a label on Account money for a purpose, living outside Salary cycles (CONTEXT.md, ADR-0011).
 * The money stays in the Accounts its [FundHolding]s name. Backed up through the Apps Script like Buckets.
 */
@Entity(tableName = "funds")
data class Fund(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Free text the user typed; may be empty. */
    val emoji: String = "",
    /** Index into the same palette as [BudgetBucket.colorIndex]. */
    val colorIndex: Int = 0,
    val targetAmount: Double? = null,
    /** yyyy-MM-dd, or null for a Fund with no due date. */
    val dueDate: String? = null,
    /** yyyy-MM-dd the Fund was archived on. An archived Fund keeps its history but holds nothing. */
    val archivedAt: String? = null,
    val sortOrder: Int = 0,
    /** yyyy-MM-dd. */
    val createdDate: String
) {
    val isArchived: Boolean get() = archivedAt != null
}

/** How a [FundHolding] holds its Account's money. Stored by name. */
enum class HoldingKind {
    /** The Account's whole balance is the holding. An Account is the WHOLE holding of at most one Fund. */
    WHOLE,

    /** A fixed amount of a shared Account. */
    FIXED;

    companion object {
        fun parse(raw: String?): HoldingKind? = entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Where some of a Fund's money sits. Deleting the Fund or the Account deletes the holding.
 * A Fund holds an Account at most once.
 */
@Entity(
    tableName = "fund_holdings",
    foreignKeys = [
        ForeignKey(entity = Fund::class, parentColumns = ["id"], childColumns = ["fundId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountRecord::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["fundId", "accountId"], unique = true), Index("accountId")]
)
data class FundHolding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fundId: Long,
    val accountId: Long,
    /** A [HoldingKind] name. */
    val kind: String,
    /** The fixed amount; only read for a FIXED holding, and 0 for a WHOLE one. */
    val amount: Double = 0.0,
    /**
     * What the holding already held on [startDate], for money that was there before tracking began.
     * History only: a holding's value is always its Account's balance (WHOLE) or [amount] (FIXED).
     */
    val openingAmount: Double = 0.0,
    /** yyyy-MM-dd. Set aside and draws count from this day on. */
    val startDate: String
) {
    val holdingKind: HoldingKind get() = HoldingKind.parse(kind) ?: HoldingKind.FIXED
}

/** A Planned contribution: how much one Salary cycle means to Set aside into one Fund. */
@Entity(
    tableName = "fund_plans",
    primaryKeys = ["fundId", "cycleId"],
    foreignKeys = [
        ForeignKey(entity = Fund::class, parentColumns = ["id"], childColumns = ["fundId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BudgetCycle::class, parentColumns = ["id"], childColumns = ["cycleId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("cycleId")]
)
data class FundPlan(
    val fundId: Long,
    val cycleId: Long,
    val plannedAmount: Double
)
