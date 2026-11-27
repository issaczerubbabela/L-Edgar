package com.issaczerubbabel.ledgar.data.remote

/**
 * One holding as backed up to the `_fund_holdings` tab. Keyed by Account **name**, because Import
 * rebuilds Account ids (ADR-0011).
 */
data class FundHoldingSyncDto(
    val accountName: String,
    val kind: String,
    val amount: Double,
    val openingAmount: Double,
    val startDate: String
)

/** One Planned contribution as backed up to the `_fund_plans` tab, keyed by its cycle's **start date**. */
data class FundPlanSyncDto(
    val cycleStartDate: String,
    val plannedAmount: Double
)

/**
 * One Fund as backed up to the `_funds` tab, with its holdings and plans nested so a restore never
 * re-links ids across tables. Sent in [SyncRequest.funds], never in `records`.
 */
data class FundSyncDto(
    val id: Long,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val targetAmount: Double?,
    val dueDate: String?,
    val archivedAt: String?,
    val sortOrder: Int,
    val createdDate: String,
    val holdings: List<FundHoldingSyncDto>,
    val plans: List<FundPlanSyncDto>
)

// Everything is nullable with a default: a restore has to cope with a hand-edited Sheet.

data class FundHoldingImportDto(
    val accountName: String? = null,
    val kind: String? = null,
    val amount: Double? = null,
    val openingAmount: Double? = null,
    val startDate: String? = null
)

data class FundPlanImportDto(
    val cycleStartDate: String? = null,
    val plannedAmount: Double? = null
)

// No id: a script that predates Funds answers with Transactions, whose string IDs would not parse as one.
data class FundImportDto(
    val name: String? = null,
    val emoji: String? = null,
    val colorIndex: Int? = null,
    val targetAmount: Double? = null,
    val dueDate: String? = null,
    val archivedAt: String? = null,
    val sortOrder: Int? = null,
    val createdDate: String? = null,
    val holdings: List<FundHoldingImportDto>? = null,
    val plans: List<FundPlanImportDto>? = null
)

data class FundsImportResponse(
    val status: String? = null,
    /** "funds" from a script that understands the target. An older script answers with the transaction list. */
    val type: String? = null,
    val data: List<FundImportDto>? = null,
    val message: String? = null,
    val scriptVersion: Int? = null
) {
    val isUnderstoodByScript: Boolean get() = type == TYPE

    companion object {
        const val TYPE = "funds"

        /** The reply type of a Funds backup from a script that understands it. */
        const val BACKED_UP_TYPE = "funds_backed_up"

        /** The first SCRIPT_VERSION with the `funds` target. */
        const val SCRIPT_VERSION = 3
    }
}
